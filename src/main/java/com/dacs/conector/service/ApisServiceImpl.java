package com.dacs.conector.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.dacs.conector.api.client.PacienteClient;
import com.dacs.conector.dto.PacienteDto;
import com.dacs.conector.dto.PaginacionDto;
import com.dacs.conector.dto.KeycloakUserDto;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import org.springframework.web.util.UriComponentsBuilder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Service
public class ApisServiceImpl implements ApisService {

    @Autowired
    private PacienteClient pacienteClient;

    @Autowired
    @Qualifier("keycloakRestTemplate")
    private RestTemplate keycloakRestTemplate;

    @Value("${keycloak.auth-server-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Override
    public PacienteDto getPacientes(int cantidad, String nacionalidad) {
        PacienteDto response = pacienteClient.search(cantidad, nacionalidad);
        return response;
    }

    @Override
    public PaginacionDto.Response<KeycloakUserDto> getUsers(int page, int size, String search) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("first", page * size)
                .queryParam("max", size);

        if (search != null && !search.isEmpty()) {
            builder.queryParam("search", search);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<KeycloakUserDto[]> response = keycloakRestTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                KeycloakUserDto[].class);

        List<KeycloakUserDto> users = Arrays.asList(response.getBody());
        
        // Obtener roles para cada usuario
        for (KeycloakUserDto user : users) {
            List<String> userRoles = getUserRoles(user.getId());
            user.setRoles(userRoles);
        }

        PaginacionDto.Response<KeycloakUserDto> paginacion = new PaginacionDto.Response<>();
        paginacion.setPagina(page);
        paginacion.setTamaño(size);
        paginacion.setContenido(users);

        return paginacion;
    }

    /**
     * Obtiene los roles de realm asignados a un usuario
     */
    private List<String> getUserRoles(String userId) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm 
                   + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RoleRepresentation[]> response = keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RoleRepresentation[].class);

            if (response.getBody() != null) {
                return Arrays.stream(response.getBody())
                        .map(RoleRepresentation::getName)
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error obteniendo roles del usuario " + userId + ": " + e.getResponseBodyAsString());
        }
        
        return Collections.emptyList();
    }

    @Override
    public String createUser(KeycloakUserDto.Create userDto) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        normalizeCreateUser(userDto);

        // Guardar los roles antes de enviar
        List<String> rolesToAssign = userDto.getRoles();
        
        System.out.println("DEBUG - Roles a asignar: " + rolesToAssign);
        
        // Keycloak no acepta el campo roles, lo limpiamos
        userDto.setRoles(null);

        HttpEntity<KeycloakUserDto.Create> request = new HttpEntity<>(userDto, headers);

        try {
            ResponseEntity<Void> response = keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Void.class);

            String location = response.getHeaders().getLocation().toString();
            String userId = location.substring(location.lastIndexOf('/') + 1);
            
            System.out.println("DEBUG - Usuario creado con ID: " + userId);

            // Asignar roles si se especificaron
            if (rolesToAssign != null && !rolesToAssign.isEmpty()) {
                System.out.println("DEBUG - Asignando roles: " + rolesToAssign);
                assignRolesToUser(userId, rolesToAssign);
            } else {
                System.out.println("DEBUG - No hay roles para asignar");
            }

            return userId;

        } catch (HttpClientErrorException e) {
            System.err.println("Error creando usuario: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error al crear usuario en Keycloak: " + e.getMessage());
        }
    }

    private void normalizeCreateUser(KeycloakUserDto.Create userDto) {
        if (userDto == null) {
            throw new IllegalArgumentException("El usuario a crear no puede ser null");
        }

        if (userDto.getEmail() == null || userDto.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio para crear el usuario");
        }

        if (userDto.getUsername() == null || userDto.getUsername().isBlank()) {
            userDto.setUsername(userDto.getEmail());
        }

        if (userDto.getCredentials() == null || userDto.getCredentials().isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una credencial de tipo password");
        }

        boolean hasPasswordCredential = userDto.getCredentials().stream()
                .anyMatch(credential -> credential != null
                        && credential.getType() != null
                        && "password".equalsIgnoreCase(credential.getType())
                        && credential.getValue() != null
                        && !credential.getValue().isBlank());

        if (!hasPasswordCredential) {
            throw new IllegalArgumentException("La credencial de contraseña es obligatoria para iniciar sesión");
        }

        userDto.setEnabled(true);
        userDto.setEmailVerified(true);
    }

    /**
     * Asigna roles de realm al usuario
     */
    private void assignRolesToUser(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = new ArrayList<>();
        
        for (String roleName : roleNames) {
            RoleRepresentation role = getRealmRole(roleName);
            if (role != null) {
                rolesToAssign.add(role);
            } else {
                System.err.println("Rol no encontrado en Keycloak: " + roleName);
            }
        }

        if (rolesToAssign.isEmpty()) {
            System.err.println("No se encontraron roles válidos para asignar");
            return;
        }

        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm 
                   + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<RoleRepresentation>> request = new HttpEntity<>(rolesToAssign, headers);

        try {
            keycloakRestTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            System.out.println("Roles asignados correctamente al usuario: " + userId);
        } catch (HttpClientErrorException e) {
            System.err.println("Error asignando roles: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error al asignar roles en Keycloak: " + e.getMessage());
        }
    }

    /**
     * Obtiene un rol del realm por nombre
     */
    private RoleRepresentation getRealmRole(String roleName) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/roles/" + roleName;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RoleRepresentation> response = keycloakRestTemplate.exchange(
                    url, HttpMethod.GET, entity, RoleRepresentation.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (HttpClientErrorException e) {
            System.err.println("Error obteniendo rol " + roleName + ": " + e.getResponseBodyAsString());
            return null;
        }
    }

    /**
     * DTO para representar un rol de Keycloak
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleRepresentation {
        private String id;
        private String name;
        private String description;
        private Boolean composite;
        private Boolean clientRole;
        private String containerId;
    }

    @Override
    public KeycloakUserDto updateUser(String userId, KeycloakUserDto.Update userDto) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        // Guardar los roles antes de enviar (Keycloak no acepta el campo roles en update)
        List<String> rolesToAssign = userDto.getRoles();
        
        System.out.println("DEBUG - Roles a actualizar: " + rolesToAssign);
        
        // Keycloak no acepta el campo roles, lo limpiamos
        userDto.setRoles(null);
        
        // Asegurar que el ID esté seteado
        userDto.setId(userId);

        HttpEntity<KeycloakUserDto.Update> request = new HttpEntity<>(userDto, headers);

        try {
            // Keycloak usa PUT para actualizar usuario
            keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    Void.class);

            System.out.println("DEBUG - Usuario actualizado con ID: " + userId);

            // Actualizar roles si se especificaron
            if (rolesToAssign != null && !rolesToAssign.isEmpty()) {
                // Primero eliminar los roles actuales
                removeAllRealmRoles(userId);
                // Luego asignar los nuevos roles
                System.out.println("DEBUG - Asignando roles: " + rolesToAssign);
                assignRolesToUser(userId, rolesToAssign);
            }

            // Obtener el usuario actualizado para devolverlo
            return getUserById(userId);

        } catch (HttpClientErrorException e) {
            System.err.println("Error actualizando usuario: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error al actualizar usuario en Keycloak: " + e.getMessage());
        }
    }

    /**
     * Obtiene un usuario por ID
     */
    @Override
    public KeycloakUserDto getUserById(String userId) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KeycloakUserDto> response = keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    KeycloakUserDto.class);

            KeycloakUserDto user = response.getBody();
            if (user != null) {
                // Obtener roles del usuario
                user.setRoles(getUserRoles(userId));
            }
            return user;

        } catch (HttpClientErrorException e) {
            System.err.println("Error obteniendo usuario: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error al obtener usuario de Keycloak: " + e.getMessage());
        }
    }

    /**
     * Elimina todos los roles de realm del usuario
     */
    private void removeAllRealmRoles(String userId) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm 
               + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Primero obtener los roles actuales
            ResponseEntity<RoleRepresentation[]> response = keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RoleRepresentation[].class);

            if (response.getBody() != null && response.getBody().length > 0) {
                // Eliminar los roles actuales
                List<RoleRepresentation> currentRoles = Arrays.asList(response.getBody());
                HttpEntity<List<RoleRepresentation>> deleteRequest = new HttpEntity<>(currentRoles, headers);
                
                keycloakRestTemplate.exchange(
                        url,
                        HttpMethod.DELETE,
                        deleteRequest,
                        Void.class);
                
                System.out.println("DEBUG - Roles anteriores eliminados");
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error eliminando roles: " + e.getResponseBodyAsString());
        }
    }

    @Override
    public KeycloakUserDto updateUserStatus(String userId, Boolean enabled) {
        String url = keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        // Primero obtener el usuario actual
        KeycloakUserDto currentUser = getUserById(userId);
        
        if (currentUser == null) {
            throw new RuntimeException("Usuario no encontrado: " + userId);
        }

        // Solo actualizar el campo enabled
        currentUser.setEnabled(enabled);
        currentUser.setRoles(null); // Keycloak no acepta este campo

        HttpEntity<KeycloakUserDto> request = new HttpEntity<>(currentUser, headers);

        try {
            keycloakRestTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    Void.class);

            System.out.println("DEBUG - Usuario " + userId + " actualizado, enabled: " + enabled);

            // Obtener el usuario actualizado para devolverlo
            return getUserById(userId);

        } catch (HttpClientErrorException e) {
            System.err.println("Error actualizando estado del usuario: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error al actualizar estado en Keycloak: " + e.getMessage());
        }
    }
}
package com.rocketpj.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    /**
     * Resuelve un mensaje a partir de una clave sin argumentos.
     *
     * @param key La clave del mensaje en messages.properties
     * @return Mensaje localizado o la propia clave si no existe
     */
    public String resolve(String key) {
        return resolve(key, null, key);
    }

    /**
     * Resuelve un mensaje con argumentos dinámicos.
     * Si no se encuentra la clave, devuelve la propia clave como fallback.
     *
     * @param key  La clave del mensaje
     * @param args Argumentos a inyectar en el mensaje
     * @return Mensaje localizado
     */
    public String resolve(String key, Object... args) {
        return resolve(key, args, key);
    }

    /**
     * Resuelve un mensaje con argumentos dinámicos y valor por defecto.
     *
     * @param key          La clave del mensaje
     * @param args         Argumentos a inyectar en el mensaje
     * @param defaultValue Valor por defecto si la clave no existe
     * @return Mensaje localizado
     */
    public String resolve(String key, Object[] args, String defaultValue) {
        try {
            return messageSource.getMessage(key, args, defaultValue, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Message key '{}' not found. Using default='{}'", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Resuelve un mensaje y lanza excepción si no existe.
     *
     * @param key  La clave del mensaje
     * @param args Argumentos dinámicos
     * @return Mensaje localizado
     * @throws IllegalArgumentException si la clave no se encuentra
     */
    public String resolveOrThrow(String key, Object... args) {
        String message = messageSource.getMessage(key, args, null, LocaleContextHolder.getLocale());
        if (message == null) {
            throw new IllegalArgumentException("Message not found for key: " + key);
        }
        return message;
    }
}

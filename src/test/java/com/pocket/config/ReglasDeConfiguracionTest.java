package com.pocket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ReglasDeConfiguracionTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(?::([^}]*))?}");
    private static final Pattern NOMBRE_DE_SECRETO = Pattern.compile("PASSWORD|SECRET|TOKEN|KEY");

    private static List<PropertySource<?>> documentos;

    @BeforeAll
    static void cargar() throws IOException {
        for (String nombre : List.of("application.yaml", "application.yml")) {
            ClassPathResource yaml = new ClassPathResource(nombre);
            if (yaml.exists()) {
                documentos = new YamlPropertySourceLoader().load("application", yaml);
                return;
            }
        }
        documentos = new PropertiesPropertySourceLoader().load("application", new ClassPathResource("application.properties"));
    }

    private static void valeEnTodosLosPerfiles(String clave, String esperado) {
        assertThat(documentos.getFirst().containsProperty(clave))
                .as("%s tiene que estar definido en la base de la configuración", clave)
                .isTrue();
        for (PropertySource<?> documento : documentos) {
            if (documento.containsProperty(clave)) {
                assertThat(String.valueOf(documento.getProperty(clave)))
                        .as("%s en %s", clave, documento.getName())
                        .isEqualTo(esperado);
            }
        }
    }

    @Test
    void flywayEsElUnicoDuenoDelEsquema() {
        valeEnTodosLosPerfiles("spring.jpa.hibernate.ddl-auto", "none");
    }

    @Test
    void loQueFaltaCargarFallaEnElRepositorio() {
        valeEnTodosLosPerfiles("spring.jpa.open-in-view", "false");
    }

    @Test
    void lasFechasSeGuardanEnUtc() {
        valeEnTodosLosPerfiles("spring.jpa.properties.hibernate.jdbc.time_zone", "UTC");
    }

    @Test
    void ningunSecretoTieneUnValorPorDefecto() {
        List<String> conDefault = new ArrayList<>();
        for (PropertySource<?> documento : documentos) {
            EnumerablePropertySource<?> propiedades = (EnumerablePropertySource<?>) documento;
            for (String clave : propiedades.getPropertyNames()) {
                Matcher placeholder = PLACEHOLDER.matcher(String.valueOf(propiedades.getProperty(clave)));
                while (placeholder.find()) {
                    String variable = placeholder.group(1);
                    String porDefecto = placeholder.group(2);
                    if (NOMBRE_DE_SECRETO.matcher(variable.toUpperCase()).find()
                            && porDefecto != null && !porDefecto.isEmpty()) {
                        conDefault.add(clave + " -> " + placeholder.group());
                    }
                }
            }
        }
        assertThat(conDefault)
                .as("secretos con default no vacío (el default vacío vale: es «sin configurar» y el servicio responde 503)")
                .isEmpty();
    }
}

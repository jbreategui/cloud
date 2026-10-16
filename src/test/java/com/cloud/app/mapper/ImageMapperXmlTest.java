package com.cloud.app.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carga ImageMapper.xml igual que al arrancar la app (sin base de datos), para detectar errores
 * de XML, de resultMap o de propiedades de ImageRecord que los tests con mocks no ven.
 */
class ImageMapperXmlTest {

    @Test
    void mapperXml_cargaYDeclaraTodasLasSentencias() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/ImageMapper.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String ns = ImageMapper.class.getName() + ".";
        for (String id : new String[]{"insert", "findAllOrderByUploadedAtDesc", "existsByObjectKey",
                "findByObjectKey", "updateProcessing"}) {
            assertThat(configuration.hasStatement(ns + id)).as(id).isTrue();
        }

        MappedStatement update = configuration.getMappedStatement(ns + "updateProcessing");
        String sql = update.getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
        assertThat(sql).contains("SET processing_status = ?").contains("blurred_key = ?").contains("WHERE object_key = ?");

        String select = configuration.getMappedStatement(ns + "findByObjectKey")
                .getBoundSql("images/a.jpg").getSql();
        assertThat(select).contains("sensitive").contains("processing_status").contains("blurred_key");
    }
}

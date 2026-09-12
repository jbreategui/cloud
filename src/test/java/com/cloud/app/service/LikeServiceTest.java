package com.cloud.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final String TABLE_NAME = "image_likes";

    @Mock
    private DynamoDbClient dynamoDbClient;

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(dynamoDbClient, TABLE_NAME);
    }

    @Test
    void like_incrementaYDevuelveElNuevoContador() {
        UpdateItemResponse response = UpdateItemResponse.builder()
                .attributes(Map.of("likes", AttributeValue.fromN("7")))
                .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        long likes = likeService.like("images/foto.jpg");

        assertThat(likes).isEqualTo(7L);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        assertThat(captor.getValue().tableName()).isEqualTo(TABLE_NAME);
        assertThat(captor.getValue().key().get("object_key").s()).isEqualTo("images/foto.jpg");
    }

    @Test
    void getLikes_conListaVacia_noLlamaADynamoYDevuelveMapaVacio() {
        Map<String, Long> likes = likeService.getLikes(List.of());

        assertThat(likes).isEmpty();
        verify(dynamoDbClient, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void getLikes_conResultados_mapeaPorObjectKey() {
        BatchGetItemResponse response = BatchGetItemResponse.builder()
                .responses(Map.of(TABLE_NAME, List.of(
                        Map.of("object_key", AttributeValue.fromS("images/a.jpg"), "likes", AttributeValue.fromN("3")),
                        Map.of("object_key", AttributeValue.fromS("images/b.jpg"), "likes", AttributeValue.fromN("9"))
                )))
                .build();
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(response);

        Map<String, Long> likes = likeService.getLikes(List.of("images/a.jpg", "images/b.jpg"));

        assertThat(likes).containsEntry("images/a.jpg", 3L).containsEntry("images/b.jpg", 9L);
    }

    @Test
    void getLikes_conMasDeCienKeys_dividEnVariosBatchGet() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            keys.add("images/img-" + i + ".jpg");
        }
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(BatchGetItemResponse.builder().responses(Map.of()).build());

        likeService.getLikes(keys);

        verify(dynamoDbClient, org.mockito.Mockito.times(2)).batchGetItem(any(BatchGetItemRequest.class));
    }
}

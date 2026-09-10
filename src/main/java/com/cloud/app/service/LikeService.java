package com.cloud.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);
    private static final String KEY_ATTRIBUTE = "object_key";
    private static final String LIKES_ATTRIBUTE = "likes";
    private static final int BATCH_GET_LIMIT = 100;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public LikeService(DynamoDbClient dynamoDbClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public long like(String objectKey) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(KEY_ATTRIBUTE, AttributeValue.fromS(objectKey)))
                .updateExpression("ADD " + LIKES_ATTRIBUTE + " :inc")
                .expressionAttributeValues(Map.of(":inc", AttributeValue.fromN("1")))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        UpdateItemResponse response = dynamoDbClient.updateItem(request);
        long likes = Long.parseLong(response.attributes().get(LIKES_ATTRIBUTE).n());

        log.info("Like registrado: objectKey={}, likes={}", objectKey, likes);
        return likes;
    }

    public Map<String, Long> getLikes(List<String> objectKeys) {
        Map<String, Long> likesByKey = new HashMap<>();
        if (objectKeys.isEmpty()) {
            return likesByKey;
        }

        for (int i = 0; i < objectKeys.size(); i += BATCH_GET_LIMIT) {
            List<String> chunk = objectKeys.subList(i, Math.min(i + BATCH_GET_LIMIT, objectKeys.size()));
            fetchChunk(chunk, likesByKey);
        }

        return likesByKey;
    }

    private void fetchChunk(List<String> objectKeys, Map<String, Long> likesByKey) {
        List<Map<String, AttributeValue>> keys = new ArrayList<>();
        for (String objectKey : objectKeys) {
            keys.add(Map.of(KEY_ATTRIBUTE, AttributeValue.fromS(objectKey)));
        }

        BatchGetItemRequest request = BatchGetItemRequest.builder()
                .requestItems(Map.of(tableName, KeysAndAttributes.builder().keys(keys).build()))
                .build();

        BatchGetItemResponse response = dynamoDbClient.batchGetItem(request);
        for (Map<String, AttributeValue> item : response.responses().getOrDefault(tableName, Collections.emptyList())) {
            String objectKey = item.get(KEY_ATTRIBUTE).s();
            long likes = Long.parseLong(item.get(LIKES_ATTRIBUTE).n());
            likesByKey.put(objectKey, likes);
        }
    }
}

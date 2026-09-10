package com.cloud.app.mapper;

import com.cloud.app.model.ImageRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageMapper {

    void insert(ImageRecord image);

    List<ImageRecord> findAllOrderByUploadedAtDesc();

    boolean existsByObjectKey(String objectKey);
}

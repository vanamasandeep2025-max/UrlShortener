package com.urlshortener.mapper;

import com.urlshortener.dto.response.ApiKeyResponse;
import com.urlshortener.entity.ApiKey;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    @Mapping(target = "plaintextKey", ignore = true)
    ApiKeyResponse toResponse(ApiKey apiKey);
}

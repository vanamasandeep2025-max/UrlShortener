package com.urlshortener.mapper;

import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.entity.Url;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * shortUrl is intentionally left out of the generated mapping: it's a runtime-configured
 * base URL (app.base-url) concatenated with the short code, not a field copied from the
 * entity, so the service layer sets it explicitly after calling toResponse().
 */
@Mapper(componentModel = "spring")
public interface UrlMapper {

    @Mapping(target = "shortUrl", ignore = true)
    @Mapping(target = "passwordProtected", expression = "java(url.isPasswordProtected())")
    UrlResponse toResponse(Url url);
}

package com.urlshortener.mapper;

import com.urlshortener.dto.response.UserResponse;
import com.urlshortener.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}

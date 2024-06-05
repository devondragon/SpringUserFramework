package com.digitalsanctuary.spring.user.api.helper;

import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.User;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiTestHelper {
    public static String buildUrlEncodedFormEntity(Object dto) {
        List<String> params = buildParams(dto);
        if ((params.size() % 2) > 0 ) {
            throw new IllegalArgumentException("Need to give an even number of parameters");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < params.size(); i += 2) {
            if (i > 0) {
                result.append('&');
            }
            result
                    .append(URLEncoder.encode(params.get(i), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(params.get(i + 1), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static List<String> buildParams(Object dto) {
        List<String> params = new ArrayList<>();
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                if (field.get(dto) == null) {
                    continue;
                }
                params.add(field.getName());
                params.add(String.valueOf(field.get(dto)));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return params;
    }

    public static User toUser(UserDto userDto) {
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setPassword(userDto.getPassword());
        return user;
    }
}

package com.digitalsanctuary.spring.user.api.helper;

import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.json.JsonUtil;
import org.junit.jupiter.api.Assertions;
import org.springframework.mock.web.MockHttpServletResponse;


public class AssertionsHelper {
    public static void compareResponses(MockHttpServletResponse servletResponse, Response excepted) throws Exception{
        Response actual = JsonUtil.readValue(servletResponse.getContentAsString(), Response.class);
        Assertions.assertEquals(actual, excepted);
    }
}

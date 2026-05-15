package com.lina.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void success_should_set_success_true_data_and_null_message() {
    ApiResponse<String> response = ApiResponse.success("hello");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("hello");
    assertThat(response.message()).isNull();
  }

  @Test
  void success_with_message_should_set_provided_message() {
    ApiResponse<Integer> response = ApiResponse.success(42, "created");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo(42);
    assertThat(response.message()).isEqualTo("created");
  }

  @Test
  void serialized_json_should_match_conventions_format() throws Exception {
    ApiResponse<String> response = ApiResponse.success("hello");

    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(node.get("success").asBoolean()).isTrue();
    assertThat(node.get("data").asText()).isEqualTo("hello");
    assertThat(node.has("message")).isTrue();
    assertThat(node.get("message").isNull()).isTrue();
  }
}

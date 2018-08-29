package com.github.uurl.kafka.serde;

import com.github.uurl.kafka.model.EmojiCount;

public class EmojiCountSerde extends WrapperSerde<EmojiCount> {

  public EmojiCountSerde() {
    super(new JsonSerializer<>(), new JsonDeserializer<>(EmojiCount.class));
  }

}

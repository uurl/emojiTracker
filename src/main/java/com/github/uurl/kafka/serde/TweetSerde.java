package com.github.uurl.kafka.serde;

import com.github.uurl.kafka.model.Tweet;

public class TweetSerde extends WrapperSerde<Tweet> {

  public TweetSerde() {
    super(new JsonSerializer<>(), new JsonDeserializer<>(Tweet.class));
  }

}

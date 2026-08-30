package com.akshat.marcellus;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "posts") // tells mongoDB to store these in a "posts" collection
public class Post {
    @Id // tells mongoDB
    private String id; // will store the filename ("12345-post.md")

    private int likes; // will store the current likes count
    private String title;
    private LocalDateTime date;
    private List<String> tags;

    // we will store the raw Markdown here now, instead of in a file!
    private String rawMarkdown;

    // This will hold the rendered HTML to send to the template, but we
    // don't necessarily need to save it to the database if we render on the fly.
    // For now, let's keep it so your templates don't break.
    private String htmlContent;
}
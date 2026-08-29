package com.akshat.marcellus;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Post {
    private String id; // will store the filename ("12345-post.md")
    private int likes; // will store the current likes count

    private String title;
    private LocalDateTime date;
    private List<String> tags;
    private String htmlContent;
}
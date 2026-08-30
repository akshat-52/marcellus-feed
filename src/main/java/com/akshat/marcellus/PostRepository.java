package com.akshat.marcellus;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    // Spring Boot automatically provides save(), findAll(), deleteById(), etc.
    // We don't need to write a single line of code here!!
}

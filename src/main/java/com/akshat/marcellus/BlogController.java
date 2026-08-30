package com.akshat.marcellus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class BlogController {

    @Autowired
    private PostRepository postRepository;

    // =========================================
    // 1. THE AUTO-MIGRATION SCRIPT
    // =========================================
    @PostConstruct
    public void migrateOldFiles() {
        if (postRepository.count() == 0) {
            System.out.println("MongoDB is empty! Migrating local files to the cloud...");
            try {
                // Read likes
                ObjectMapper objectMapper = new ObjectMapper();
                Path likesPath = Paths.get("src/main/resources/likes.json");
                Map<String, Integer> likesMap = new HashMap<>();
                if (Files.exists(likesPath)) {
                    likesMap = objectMapper.readValue(Files.readString(likesPath), new TypeReference<Map<String, Integer>>() {});
                }

                // Read markdown files
                Path postsDirectory = Paths.get("src/main/resources/posts");
                File[] files = postsDirectory.toFile().listFiles((dir, name) -> name.endsWith(".md"));

                List<Extension> extensions = List.of(YamlFrontMatterExtension.create());
                Parser parser = Parser.builder().extensions(extensions).build();

                if (files != null) {
                    for (File file : files) {
                        String markdown = new String(Files.readAllBytes(file.toPath()));
                        Node document = parser.parse(markdown);
                        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
                        document.accept(visitor);
                        Map<String, List<String>> metadata = visitor.getData();

                        Post post = new Post();
                        post.setId(UUID.randomUUID().toString()); // MongoDB uses unique string IDs
                        post.setLikes(likesMap.getOrDefault(file.getName(), 0));

                        post.setTitle(metadata != null && metadata.containsKey("title") ? metadata.get("title").get(0) : "Untitled");

                        if (metadata != null && metadata.containsKey("date")) {
                            String dateString = metadata.get("date").get(0);
                            post.setDate(dateString.contains("T") ? LocalDateTime.parse(dateString) : LocalDate.parse(dateString).atStartOfDay());
                        } else {
                            post.setDate(LocalDateTime.now());
                        }

                        if (metadata != null && metadata.containsKey("tags")) {
                            List<String> cleanTags = new ArrayList<>();
                            for (String raw : metadata.get("tags")) {
                                String[] splitTags = raw.replace("[", "").replace("]", "").split(",");
                                for (String t : splitTags) {
                                    if (!t.trim().isEmpty()) cleanTags.add(t.trim());
                                }
                            }
                            post.setTags(cleanTags);
                        }

                        // Strip frontmatter before saving to database
                        String rawBody = markdown;
                        if (markdown.startsWith("---")) {
                            int endOfFrontMatter = markdown.indexOf("---", 3);
                            if (endOfFrontMatter != -1) rawBody = markdown.substring(endOfFrontMatter + 3).trim();
                        }
                        post.setRawMarkdown(rawBody);

                        postRepository.save(post); // Send to MongoDB!
                    }
                }
                System.out.println("Migration complete! Your local posts are now in MongoDB.");
            } catch (Exception e) {
                System.err.println("Migration failed!");
                e.printStackTrace();
            }
        }
    }

    // =========================================
    // 2. THE MAIN FEED (READ FROM MONGODB)
    // =========================================
    @GetMapping("/")
    public String home(@RequestParam(required = false) String query,
                       @RequestParam(required = false) String tag,
                       Model model) {

        // Pull all posts directly from the cloud database
        List<Post> blogPosts = postRepository.findAll();

        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        // Convert the raw markdown stored in the database into HTML for the browser
        for (Post post : blogPosts) {
            Node document = parser.parse(post.getRawMarkdown() != null ? post.getRawMarkdown() : "");
            post.setHtmlContent(renderer.render(document));
        }

        // Apply filters
        if (tag != null && !tag.trim().isEmpty()) {
            String searchTag = tag.trim().toLowerCase();
            blogPosts = blogPosts.stream()
                    .filter(p -> p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().equals(searchTag)))
                    .collect(Collectors.toList());
        }

        if (query != null && !query.trim().isEmpty()) {
            String lowerQuery = query.trim().toLowerCase();
            blogPosts = blogPosts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(lowerQuery) ||
                            (p.getHtmlContent() != null && p.getHtmlContent().toLowerCase().contains(lowerQuery)) ||
                            (p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery))))
                    .collect(Collectors.toList());
        }

        blogPosts.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        model.addAttribute("posts", blogPosts);

        return "index";
    }

    // =========================================
    // 3. CREATE NEW POST
    // =========================================
    @GetMapping("/admin")
    public String adminDashboard() { return "admin"; }

    @GetMapping("/about")
    public String aboutPage() { return "about"; }

    @PostMapping("/admin/post")
    public String createPost(@RequestParam String title,
                             @RequestParam String tags,
                             @RequestParam String content) {

        Post post = new Post();
        post.setTitle(title);
        post.setDate(LocalDateTime.now());
        post.setLikes(0);
        post.setRawMarkdown(content); // No more YAML frontmatter needed!

        List<String> tagList = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
        post.setTags(tagList);

        // Save directly to MongoDB
        postRepository.save(post);

        return "redirect:/";
    }

    // =========================================
    // 4. LIKE A POST
    // =========================================
    @PostMapping("/like")
    public String likePost(@RequestParam String id) {
        postRepository.findById(id).ifPresent(post -> {
            post.setLikes(post.getLikes() + 1);
            postRepository.save(post); // Update MongoDB
        });
        return "redirect:/";
    }

    // =========================================
    // 5. EDIT A POST
    // =========================================
    @GetMapping("/admin/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Post post = postRepository.findById(id).orElse(new Post());

        model.addAttribute("id", post.getId());
        model.addAttribute("title", post.getTitle());
        model.addAttribute("tags", post.getTags() != null ? String.join(", ", post.getTags()) : "");
        model.addAttribute("content", post.getRawMarkdown());
        return "edit";
    }

    @PostMapping("/admin/edit")
    public String updatePost(@RequestParam String id,
                             @RequestParam String title,
                             @RequestParam String tags,
                             @RequestParam String content) {

        postRepository.findById(id).ifPresent(post -> {
            post.setTitle(title);
            post.setRawMarkdown(content);
            List<String> tagList = Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
            post.setTags(tagList);

            postRepository.save(post); // Overwrite in MongoDB
        });

        return "redirect:/";
    }

    // =========================================
    // 6. DELETE A POST
    // =========================================
    @PostMapping("/admin/delete")
    public String deletePost(@RequestParam String id) {
        postRepository.deleteById(id); // Instantly gone from the cloud
        return "redirect:/";
    }
}
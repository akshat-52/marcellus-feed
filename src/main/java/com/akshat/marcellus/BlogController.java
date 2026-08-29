package com.akshat.marcellus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import java.io.File;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class BlogController {

    // Setup our JSON tools and file path
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path likesPath = Paths.get("src/main/resources/likes.json");

    // Helper: Read the JSON file and turn in into a Java Map
    private Map<String, Integer> getLikes() throws Exception {
        if (!Files.exists(likesPath)) {
            // If the file doesn't exist yet, create an empty one
            Files.writeString(likesPath, "{}");
        }
        String json = Files.readString(likesPath);
        return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
    }

    // Helper: Turn the java Map back into JSON and save it
    private void saveLikes(Map<String, Integer> likes) throws Exception {
        String json = objectMapper.writeValueAsString(likes);
        Files.writeString(likesPath, json);
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String query,
                       @RequestParam(required = false) String tag,
                       Model model) throws Exception {

        Path postsDirectory = Paths.get("src/main/resources/posts");
        File[] files = postsDirectory.toFile().listFiles((dir, name) -> name.endsWith(".md"));

        List<Extension> extensions = List.of(YamlFrontMatterExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        List<Post> blogPosts = new ArrayList<>();

        // Load our likes database once before the loop starts
        Map<String, Integer> likesMap = getLikes();

        if (files != null) {
            for (File file : files) {
                String markdown = new String(Files.readAllBytes(file.toPath()));
                Node document = parser.parse(markdown);

                YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
                document.accept(visitor);
                Map<String, List<String>> metadata = visitor.getData();

                Post post = new Post();

                // Set the ID (filename) and grab the like count
                post.setId(file.getName());
                post.setLikes(likesMap.getOrDefault(file.getName(), 0));

                if (metadata != null && metadata.containsKey("title")) {
                    post.setTitle(metadata.get("title").get(0));
                } else {
                    post.setTitle("Untitled");
                }

                if (metadata != null && metadata.containsKey("date")) {
                    String dateString = metadata.get("date").get(0);
                    if (dateString.contains("T")) {
                        post.setDate(LocalDateTime.parse(dateString));
                    } else {
                        post.setDate(LocalDate.parse(dateString).atStartOfDay());
                    }
                }

                // BUG FIX #2: Clean up the brackets and split tags properly
                if (metadata != null && metadata.containsKey("tags")) {
                    List<String> rawTags = metadata.get("tags");
                    List<String> cleanTags = new ArrayList<>();
                    for (String raw : rawTags) {
                        // Strip out [ and ] and split by commas
                        String[] splitTags = raw.replace("[", "").replace("]", "").split(",");
                        for (String t : splitTags) {
                            if (!t.trim().isEmpty()) {
                                cleanTags.add(t.trim());
                            }
                        }
                    }
                    post.setTags(cleanTags);
                }

                post.setHtmlContent(renderer.render(document));
                blogPosts.add(post);
            }
        }

        // --- FILTERING LOGIC ---

        if (tag != null && !tag.trim().isEmpty()) {
            String searchTag = tag.trim().toLowerCase();
            blogPosts = blogPosts.stream()
                    .filter(p -> p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().equals(searchTag)))
                    .collect(Collectors.toList()); // BUG FIX #1: Mutable list!
        }

        if (query != null && !query.trim().isEmpty()) {
            String lowerQuery = query.trim().toLowerCase();
            blogPosts = blogPosts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(lowerQuery) ||
                            (p.getHtmlContent() != null && p.getHtmlContent().toLowerCase().contains(lowerQuery)) ||
                            (p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery))))
                    .collect(Collectors.toList()); // BUG FIX #1: Mutable list!
        }

        // ---------------------------

        // This will now sort perfectly without crashing!
        blogPosts.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        model.addAttribute("posts", blogPosts);

        return "index";
    }

    // 1. This route just displays the dashboard HTML page
    @GetMapping("/admin")
    public String adminDashboard() {
        return "admin";
    }

    // 2. This route intercepts the data when you click "Publish"
    @PostMapping("/admin/post")
    public String createPost(@RequestParam String title,
                             @RequestParam String tags,
                             @RequestParam String content) throws Exception {

        // Grab exactly what time it is right now
        LocalDateTime today = LocalDateTime.now();

        // Construct the raw text for our Markdown file, including the YAML frontmatter
        String fileContent = "---\n" +
                "title: " + title + "\n" +
                "date: " + today.toString() + "\n" +
                "tags: [" + tags + "]\n" +
                "---\n\n" +
                content;

        // Generate a unique filename using a timestamp so files never overwrite each other
        String filename = System.currentTimeMillis() + "-" + title.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase() + ".md";

        // Tell Java exactly where to save this new file
        Path path = Paths.get("src/main/resources/posts/" + filename);
        Files.write(path, fileContent.getBytes());

        // Redirect the user back to the homepage to see their new post!
        return "redirect:/";
    }

    @PostMapping("/like")
    public String likePost(@RequestParam String id) throws Exception {
        // load the current likes
        Map<String, Integer> likesMap = getLikes();

        // find the current score for this specific post (dafult to 0 if none)
        int currentLikes = likesMap.getOrDefault(id, 0);

        // add 1 and update the map
        likesMap.put(id, currentLikes + 1);

        // save the map back to the JSON file
        saveLikes(likesMap);

        return "redirect:/";
    }

    @GetMapping("/admin/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model) throws Exception {
        // 1. Find the file using the filename (id)
        Path filePath = Paths.get("src/main/resources/posts", id);
        String markdown = Files.readString(filePath);

        // 2. Extract just the raw body of the post (everything after the YAML)
        String rawBody = markdown;
        if (markdown.startsWith("---")) {
            int endOfFrontMatter = markdown.indexOf("---", 3);
            if (endOfFrontMatter != -1) {
                rawBody = markdown.substring(endOfFrontMatter + 3).trim();
            }
        }

        // 3. Parse the frontmatter to get the Title and Tags using your existing CommonMark setup
        List<Extension> extensions = List.of(YamlFrontMatterExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        Node document = parser.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> metadata = visitor.getData();

        String title = metadata != null && metadata.containsKey("title") ? metadata.get("title").get(0) : "";
        String tags = "";
        if (metadata != null && metadata.containsKey("tags")) {
            tags = String.join(", ", metadata.get("tags")).replace("[", "").replace("]", "");
        }

        // 4. Send the extracted data to the HTML template
        model.addAttribute("id", id);
        model.addAttribute("title", title);
        model.addAttribute("tags", tags);
        model.addAttribute("content", rawBody);

        return "edit";
    }

    @PostMapping("/admin/edit")
    public String updatePost(@RequestParam String id,
                             @RequestParam String title,
                             @RequestParam String tags,
                             @RequestParam String content) throws Exception {

        Path filePath = Paths.get("src/main/resources/posts", id);
        String oldMarkdown = Files.readString(filePath);
        String dateStr = LocalDateTime.now().toString(); // Fallback if no date is found

        // 1. Fetch the original date so editing doesn't ruin your timeline
        List<Extension> extensions = List.of(YamlFrontMatterExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        Node document = parser.parse(oldMarkdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> metadata = visitor.getData();

        if (metadata != null && metadata.containsKey("date")) {
            dateStr = metadata.get("date").get(0);
        }

        // 2. Rebuild the complete Markdown file
        String updatedFileContent = "---\n" +
                "title: " + title + "\n" +
                "date: " + dateStr + "\n" +
                "tags: [" + tags + "]\n" +
                "---\n\n" +
                content;

        // 3. Overwrite the file on the hard drive
        Files.writeString(filePath, updatedFileContent);

        return "redirect:/";
    }
}
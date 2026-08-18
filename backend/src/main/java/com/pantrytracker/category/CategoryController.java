package com.pantrytracker.category;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryView> list() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(),
                        c.getDefaultShelfLifeDays(), c.getWarningThresholdDays()))
                .toList();
    }

    public record CategoryView(UUID id, String name,
                               int defaultShelfLifeDays, int warningThresholdDays) {}
}
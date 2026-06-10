package com.example.solimus.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/syndic")
@Tag(name = "4. Syndic", description = "Endpoints réservés aux syndics")
public class SyndicController {
}
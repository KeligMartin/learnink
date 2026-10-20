package com.learnink.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Le frontend React (compilé dans static/) gère ses propres routes : on renvoie index.html
 * pour qu'un rechargement ou un lien direct vers /exercises/12 fonctionne.
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/change-password", "/admin", "/exercises/{id}"})
    public String index() {
        return "forward:/index.html";
    }
}

package edu.tku.registration.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes to the React SPA. Any GET whose first segment is not {@code api}
 * or {@code assets} and contains no {@code .} goes to {@code /index.html}; everything else falls
 * through to the static resource handler.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/{path:(?!api$|assets$)[^.]*}", "/{path:(?!api$|assets$)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}

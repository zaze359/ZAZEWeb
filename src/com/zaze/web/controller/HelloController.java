package com.zaze.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Description :
 *
 * @author : zaze
 * @version : 2017/2/21
 */
@Controller
@RequestMapping(value = "hello", method = RequestMethod.GET)
public class HelloController {

    @RequestMapping(value = "hi", method = RequestMethod.GET)
    public String sayHi(ModelMap modelMap) {
        modelMap.addAttribute("name", "zaze");
        modelMap.addAttribute("msg", "this is zaze's test code!");
        // 返回对应的get
        return "hello";
    }
}

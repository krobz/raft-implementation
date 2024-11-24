package com.github.raftimpl.raft.controller;

import com.github.raftimpl.raft.template.RaftTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/raft")
public class TestController {
    @Autowired
    private RaftTemplate raftTemplate;

    @PostMapping("/write")
    public String write(@RequestParam String key, @RequestParam String value) {
        return raftTemplate.write(key, value);
    }

    @GetMapping("/read")
    public String read(@RequestParam String key) {
        return raftTemplate.read(key);
    }
}

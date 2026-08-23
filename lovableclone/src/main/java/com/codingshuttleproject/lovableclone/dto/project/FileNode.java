package com.codingshuttleproject.lovableclone.dto.project;

import java.time.Instant;

public record FileNode (
        String path
){
    @Override
    public String toString() {
        return path;
    }
}

package com.shipcad.review.domain;

import java.util.ArrayList;
import java.util.List;

public class AiExplanation {
    public String model;
    public String summary;
    public String reason;
    public String basis;
    public String recommendation;
    public String reviewFocus;
    public List<String> evidenceRefs = new ArrayList<>();
}

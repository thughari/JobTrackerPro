package com.thughari.jobtrackerpro.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ChartData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private long value;
}
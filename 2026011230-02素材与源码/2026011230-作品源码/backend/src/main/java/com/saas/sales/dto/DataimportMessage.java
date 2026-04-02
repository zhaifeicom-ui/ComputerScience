package com.saas.sales.dto;

import java.io.Serializable;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataimportMessage implements Serializable {
    private Long jobId;
    private Long tenantId;
    private byte[] fileBytes ;
    private String fileName;
}

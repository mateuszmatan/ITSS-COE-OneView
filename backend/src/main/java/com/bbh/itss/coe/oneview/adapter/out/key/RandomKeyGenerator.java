package com.bbh.itss.coe.oneview.adapter.out.key;

import com.bbh.itss.coe.oneview.domain.pipeline.KeyGenerator;
import org.springframework.stereotype.Component;

import static java.util.UUID.randomUUID;

@Component
class RandomKeyGenerator implements KeyGenerator {

    @Override
    public String newKey() {
        return randomUUID().toString();
    }
}

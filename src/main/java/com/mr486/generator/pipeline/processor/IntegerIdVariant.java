package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import org.springframework.stereotype.Component;

/**
 * Stratégie d'identifiant {@code Integer} : substitue {@code Long} par {@code Integer} dans l'entité
 * et le paramètre générique du repository.
 */
@Component
public class IntegerIdVariant implements IdVariant {

    @Override
    public IdType type() {
        return IdType.INTEGER;
    }

    @Override
    public String apply(String text, String path, ResourceModuleRequest res) {
        return text
            .replace("private Long id", "private Integer id")
            .replace("JpaRepository<" + res.getClassName() + ", Long>",
                     "JpaRepository<" + res.getClassName() + ", Integer>");
    }
}

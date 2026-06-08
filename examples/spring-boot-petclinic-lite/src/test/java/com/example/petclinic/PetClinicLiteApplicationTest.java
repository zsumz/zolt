package com.example.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.ExtendedModelMap;

@SpringBootTest
final class PetClinicLiteApplicationTest {
    @Autowired
    private OwnerRepository owners;

    @Autowired
    private OwnerController controller;

    @Test
    void loadsOwnersAndRendersHomePage() {
        assertThat(owners.findAll())
                .extracting(Owner::name)
                .contains("Ada Lovelace", "Grace Hopper");

        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(controller.home(model)).isEqualTo("owners/list");
        assertThat(model.getAttribute("owners")).isNotNull();
    }
}

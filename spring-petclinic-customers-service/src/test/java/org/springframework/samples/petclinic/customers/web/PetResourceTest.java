package org.springframework.samples.petclinic.customers.web;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.customers.model.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest({OwnerResource.class, PetResource.class})
@ActiveProfiles("test")
class OwnerPetResourceTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    OwnerRepository ownerRepository;

    @MockBean
    PetRepository petRepository;

    private Owner owner;
    private Pet pet;

    @BeforeEach
    void setUp() {
        owner = new Owner();
        owner.setFirstName("John");
        owner.setLastName("Doe");

        pet = new Pet();
        pet.setId(2);
        pet.setName("Basil");
        PetType petType = new PetType();
        petType.setId(6);
        pet.setType(petType);
        owner.addPet(pet);
    }

    @Test
    void shouldGetOwnerById() throws Exception {
        given(ownerRepository.findById(1)).willReturn(Optional.of(owner));

        mvc.perform(get("/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.firstName").value("John"))
            .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void shouldReturnNotFoundForNonExistingOwner() throws Exception {
        given(ownerRepository.findById(99)).willReturn(Optional.empty());

        mvc.perform(get("/owners/99").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetPetById() throws Exception {
        given(petRepository.findById(2)).willReturn(Optional.of(pet));

        mvc.perform(get("/owners/1/pets/2").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.name").value("Basil"))
            .andExpect(jsonPath("$.type.id").value(6));
    }

    @Test
    void shouldReturnNotFoundForNonExistingPet() throws Exception {
        given(petRepository.findById(99)).willReturn(Optional.empty());

        mvc.perform(get("/owners/1/pets/99").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }
}

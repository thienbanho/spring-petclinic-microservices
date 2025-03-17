package org.springframework.samples.petclinic.customers.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.customers.model.Owner;
import org.springframework.samples.petclinic.customers.model.OwnerRepository;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OwnerResource.class)
class OwnerResourceTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    OwnerRepository ownerRepository;

    @Test
    void shouldGetAnOwnerInJSONFormat() throws Exception {
        Owner owner = new Owner();
        owner.setFirstName("John");
        owner.setLastName("Doe");

        given(ownerRepository.findById(1)).willReturn(Optional.of(owner));

        mvc.perform(get("/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
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
    void shouldGetAllOwners() throws Exception {
        Owner owner1 = new Owner();
        owner1.setFirstName("John");

        Owner owner2 = new Owner();
        owner2.setFirstName("Jane");

        given(ownerRepository.findAll()).willReturn(Arrays.asList(owner1, owner2));

        mvc.perform(get("/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].firstName").value("John"))
            .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    @Test
    void shouldCreateOwner() throws Exception {
        String jsonOwner = "{ \"firstName\": \"Alice\", \"lastName\": \"Smith\", \"address\": \"123 Main St\", \"city\": \"NYC\", \"telephone\": \"1234567890\" }";

        Owner savedOwner = new Owner();
        savedOwner.setFirstName("Alice");
        savedOwner.setLastName("Smith");

        when(ownerRepository.save(any(Owner.class))).thenReturn(savedOwner);

        mvc.perform(post("/owners")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonOwner))
            .andExpect(status().isCreated());
    }

    @Test
    void shouldUpdateOwner() throws Exception {
        Owner existingOwner = new Owner();
        existingOwner.setFirstName("Old Name");

        given(ownerRepository.findById(1)).willReturn(Optional.of(existingOwner));

        String updatedJsonOwner = "{ \"firstName\": \"New Name\", \"lastName\": \"Smith\", \"address\": \"123 Main St\", \"city\": \"NYC\", \"telephone\": \"1234567890\" }";

        mvc.perform(put("/owners/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatedJsonOwner))
            .andExpect(status().isNoContent());
    }
}

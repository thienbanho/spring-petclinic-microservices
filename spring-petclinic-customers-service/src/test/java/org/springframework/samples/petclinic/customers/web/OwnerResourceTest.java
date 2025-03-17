package org.springframework.samples.petclinic.customers.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.customers.model.Owner;
import org.springframework.samples.petclinic.customers.model.OwnerRepository;
import org.springframework.samples.petclinic.customers.web.mapper.OwnerEntityMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OwnerResource.class)
@ActiveProfiles("test")
class OwnerResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OwnerRepository ownerRepository;

    @MockBean
    private OwnerEntityMapper ownerEntityMapper;

    @Test
    void testFindOwnerById_Success() throws Exception {
        Owner owner = new Owner();
        owner.setFirstName("John");
        owner.setLastName("Doe");
        owner.setAddress("123 Street");
        owner.setCity("New York");
        owner.setTelephone("1234567890");

        given(ownerRepository.findById(1)).willReturn(Optional.of(owner));

        mockMvc.perform(get("/owners/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("John"))
            .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void testFindOwnerById_NotFound() throws Exception {
        given(ownerRepository.findById(1)).willReturn(Optional.empty());

        mockMvc.perform(get("/owners/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void testFindAllOwners() throws Exception {
        Owner owner1 = new Owner();
        owner1.setFirstName("John");
        owner1.setLastName("Doe");

        Owner owner2 = new Owner();
        owner2.setFirstName("Jane");
        owner2.setLastName("Smith");

        given(ownerRepository.findAll()).willReturn(Arrays.asList(owner1, owner2));

        mockMvc.perform(get("/owners"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].firstName").value("John"))
            .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    @Test
    void testCreateOwner() throws Exception {
        Owner owner = new Owner();
        owner.setFirstName("Alice");
        owner.setLastName("Brown");
        owner.setAddress("456 Street");
        owner.setCity("Los Angeles");
        owner.setTelephone("0987654321");

        given(ownerRepository.save(any(Owner.class))).willReturn(owner);

        mockMvc.perform(post("/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Alice",
                        "lastName": "Brown",
                        "address": "456 Street",
                        "city": "Los Angeles",
                        "telephone": "0987654321"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.lastName").value("Brown"));
    }

    @Test
    void testUpdateOwner() throws Exception {
        Owner existingOwner = new Owner();
        existingOwner.setFirstName("Bob");
        existingOwner.setLastName("Miller");

        given(ownerRepository.findById(1)).willReturn(Optional.of(existingOwner));
        given(ownerRepository.save(any(Owner.class))).willReturn(existingOwner);

        mockMvc.perform(put("/owners/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "firstName": "Bob",
                        "lastName": "Miller",
                        "address": "789 Street",
                        "city": "Chicago",
                        "telephone": "1122334455"
                    }
                """))
            .andExpect(status().isNoContent());
    }
}

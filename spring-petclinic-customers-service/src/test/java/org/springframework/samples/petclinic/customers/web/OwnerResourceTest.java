package org.springframework.samples.petclinic.customers.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.customers.model.Owner;
import org.springframework.samples.petclinic.customers.model.OwnerRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OwnerResource.class)
@ActiveProfiles("test")
class OwnerResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OwnerRepository ownerRepository;

    @BeforeEach
    void setup() {
        Owner owner = new Owner();
        owner.setFirstName("John");
        owner.setLastName("Doe");
        owner.setAddress("123 Street");
        owner.setCity("New York");
        owner.setTelephone("1234567890");
        given(ownerRepository.save(any(Owner.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(ownerRepository.findById(1))
            .willReturn(Optional.of(owner));
    }

    @Test
    void testCreateOwner() throws Exception {
        String ownerJson = """
            {
                "firstName": "Alice",
                "lastName": "Brown",
                "address": "456 Street",
                "city": "Los Angeles",
                "telephone": "0987654321"
            }
        """;

        String response = mockMvc.perform(post("/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.lastName").value("Brown"))
            .andExpect(jsonPath("$.address").value("456 Street"))
            .andExpect(jsonPath("$.city").value("Los Angeles"))
            .andExpect(jsonPath("$.telephone").value("0987654321"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        System.out.println("API Response: " + response);
    }
}

package com.vedran.cardapi.repo;

import com.vedran.cardapi.models.Client;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.domain.Example;
import java.util.List;

public interface ClientRepo extends CrudRepository<Client, Long>{
    List<Client> findByLastName(String lastname);

    List<Client> findByOib(Long oib);

    List<Client> findByFirstName(String firstname);

    Iterable<Client> findAll(Example<Client> example);

    List<Client> findByFirstNameAndLastName(String firstname, String lastname);
}

package com.crosscert.fidoadmin.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface AdminRepository<E, ID> extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {
}

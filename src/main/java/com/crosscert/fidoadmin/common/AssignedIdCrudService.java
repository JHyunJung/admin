package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.audit.AuditLogger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 할당형 PK(문자열 PK, COMPANY_IDX PK, 복합키) 테이블용 CRUD 서비스.
 *
 * Spring Data 의 save() 는 식별자가 채워진 엔티티를 "기존 행" 으로 보고 MERGE 한다.
 * 등록 화면에서 이미 있는 키를 입력하면 오류 없이 기존 행이 덮어써지므로,
 * 등록은 존재 검사 후 EntityManager.persist 로만 수행한다. 수정·삭제는 상위 클래스 그대로다.
 */
public abstract class AssignedIdCrudService<E, ID, S extends SearchForm> extends CrudService<E, ID, S> {

    protected final EntityManager em;

    protected AssignedIdCrudService(AdminRepository<E, ID> repository, AuditLogger audit, EntityManager em) {
        super(repository, audit);
        this.em = em;
    }

    /** 폼에서 채워진 식별자. null/공백이면 등록을 거부한다. */
    protected abstract ID assignedId(E entity);

    @Override
    protected E insert(E entity) {
        ID id = assignedId(entity);
        if (id == null || (id instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(tableName() + " 식별자가 비어 있습니다");
        }
        if (repository.existsById(id)) {
            throw new DataIntegrityViolationException(tableName() + " " + id + " 은(는) 이미 존재합니다");
        }
        try {
            em.persist(entity);
            em.flush(); // 존재 검사와 INSERT 사이의 경쟁은 PK 제약이 잡는다. 여기서 바로 드러나게 한다.
        } catch (PersistenceException e) {
            throw new DataIntegrityViolationException(tableName() + " " + id + " 저장 실패", e);
        }
        return entity;
    }
}

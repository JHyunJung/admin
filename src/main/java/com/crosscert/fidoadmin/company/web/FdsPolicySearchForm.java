package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;

/**
 * FDS 정책 검색 폼. 고유 검색 조건이 없다 — 목록은 유효 테넌트로만 걸러진다
 * (CrudService.search() 가 companyIdxAttribute() 로 강제).
 *
 * <p>Task 10 이전에는 SUPER 가 고객사(companyIdx)로 목록을 필터링했지만, 이제 테넌트는
 * 세션이 정하므로 그 select 를 화면에서 뺐다. 클래스는 CrudController 의 제네릭
 * 시그니처(FdsPolicyController extends CrudController&lt;..., FdsPolicySearchForm&gt;) 때문에
 * 남겨 둔다 — 지우면 그 파급이 이 작업 범위를 벗어난다.
 */
public class FdsPolicySearchForm extends SearchForm {
}

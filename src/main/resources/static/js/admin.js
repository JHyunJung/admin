document.addEventListener('DOMContentLoaded', function () {
  var modalEl = document.getElementById('confirmModal');
  if (!modalEl || typeof bootstrap === 'undefined') return;
  var modal = new bootstrap.Modal(modalEl);
  var okBtn = document.getElementById('confirmModalOk');
  var defaultMessage = modalEl.querySelector('.modal-body').textContent;
  var defaultOk = okBtn.textContent;
  var targetFormId = null;
  // 모달에서 확인을 눌러 스스로 제출한 폼. 아래 submit 가드가 이 폼만 통과시킨다.
  var approvedForm = null;

  function openModal(btn, formId) {
    targetFormId = formId;
    modalEl.querySelector('.modal-body').textContent = btn.getAttribute('data-confirm-message') || defaultMessage;
    okBtn.textContent = btn.getAttribute('data-confirm-ok') || defaultOk;
    okBtn.classList.toggle('btn-danger', !btn.hasAttribute('data-confirm-ok'));
    okBtn.classList.toggle('btn-primary', btn.hasAttribute('data-confirm-ok'));
    modal.show();
  }

  document.querySelectorAll('[data-confirm-form]').forEach(function (btn) {
    var formId = btn.getAttribute('data-confirm-form');
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openModal(btn, formId);
    });

    // 확인 버튼을 누르는 길 말고도 폼이 제출되는 길이 있다. 텍스트 입력이 하나뿐이고
    // 제출 버튼이 없는 폼은 입력란에서 Enter 만 쳐도 브라우저가 암묵적 제출(implicit
    // submission)을 일으켜 확인 모달을 건너뛴다. 가입 승인 화면의 거절 사유가 그 경우다.
    // 그래서 확인을 버튼이 아니라 폼의 submit 에 건다. 새 화면이 확인 폼에 입력란을
    // 덧붙여도 같은 함정에 빠지지 않는다.
    var form = document.getElementById(formId);
    if (!form || form.dataset.confirmGuarded) return;
    form.dataset.confirmGuarded = '1';
    form.addEventListener('submit', function (e) {
      // 모달에서 승인받은 제출은 그대로 통과시킨다. 막으면 확인해도 아무 일이 없다.
      if (approvedForm === form) return;
      e.preventDefault();
      openModal(btn, formId);
    });
  });

  okBtn.addEventListener('click', function () {
    var form = targetFormId && document.getElementById(targetFormId);
    if (!form) { modal.hide(); return; }
    // 검증 실패 시 브라우저 말풍선이 모달 뒤에 가려지지 않도록 모달을 먼저 닫는다.
    modal.hide();
    // form.submit() 은 명세상 제약 검증(required 등)을 건너뛴다. requestSubmit() 을 써야
    // 브라우저가 미입력을 먼저 잡아 준다. 제출 버튼이 없는 폼에 인자 없이 부르는 것은
    // 정상이며, 이 화면들의 확인 버튼은 모두 type="button" 이라 문제되지 않는다.
    // requestSubmit() 은 submit 이벤트를 동기로 쏘므로, 표식을 바로 앞뒤로 여닫으면
    // 그 사이에 다른 제출이 끼어들 틈이 없다. 검증에 걸려 이벤트가 아예 안 떠도
    // finally 가 표식을 걷어 내므로 표식이 남아 가드가 뚫리는 일은 없다.
    approvedForm = form;
    try {
      if (form.requestSubmit) form.requestSubmit();
      else form.submit();
    } finally {
      approvedForm = null;
    }
  });
});

// 고객사 선택 화면의 이름 검색. 행이 많아도 서버를 다시 부르지 않는다.
// data-tenant-card 는 목록의 각 행(tr)에 붙는다 — 표시 방식과 무관하게 이 표식만 보므로
// 카드에서 목록으로 바꿀 때 이 코드는 그대로 두었다.
(function () {
  var input = document.getElementById('tenantFilter');
  if (!input) return;
  input.addEventListener('input', function () {
    var q = input.value.trim().toLowerCase();
    document.querySelectorAll('[data-tenant-card]').forEach(function (card) {
      var name = (card.getAttribute('data-name') || '').toLowerCase();
      card.style.display = name.indexOf(q) === -1 ? 'none' : '';
    });
  });
})();

// 고객사 선택 화면: 행 아무 데나 클릭하면 그 고객사를 선택한다.
//
// 행 안의 폼을 제출하는 방식이라, 스크립트가 없으면 고객사 이름(submit 버튼)을 눌러
// 선택하는 길이 그대로 남는다. tr 에 onclick 만 다는 방식을 쓰지 않는 이유다 —
// 그러면 키보드·스크린리더 사용자가 선택할 방법이 없어진다.
(function () {
  var rows = document.querySelectorAll('[data-tenant-row]');
  if (!rows.length) return;

  rows.forEach(function (row) {
    var form = row.querySelector('[data-tenant-form]');
    if (!form) return;

    row.style.cursor = 'pointer';
    row.addEventListener('click', function (e) {
      // 이름 버튼을 직접 누른 경우는 브라우저가 알아서 제출한다. 여기서 또 제출하면
      // 같은 폼이 두 번 나간다.
      if (e.target.closest('button, a, input, label')) return;
      // 텍스트를 드래그해 선택하려던 것이면 제출하지 않는다.
      var sel = window.getSelection();
      if (sel && sel.toString().length) return;
      if (form.requestSubmit) form.requestSubmit();
      else form.submit();
    });
  });
})();

// 목록 표: 행 아무 데나 클릭하면 그 행의 상세로 간다.
//
// 대표값 링크(<a>)는 그대로 둔다. 이 스크립트는 그 링크를 "행이 가리키는 곳"으로 읽어
// 쓸 뿐이라, 스크립트가 없으면 예전처럼 대표값을 눌러 들어가면 된다. tr 에 onclick 을
// 심는 방식을 쓰지 않는 이유이기도 하다 — 그러면 키보드·스크린리더로 들어갈 길이 사라진다.
//
// 문서 하나에 리스너 하나만 건다(이벤트 위임). 행이 수백 개여도 비용이 같고,
// 나중에 추가되는 목록 화면도 표식을 달 필요 없이 그대로 동작한다.
(function () {
  // 행이 가리키는 곳. 첫 번째 링크를 대표로 본다(목록 템플릿은 행당 상세 링크가 하나다).
  function rowHref(row) {
    var a = row.querySelector('a[href]');
    return a && a.getAttribute('href');
  }

  document.addEventListener('click', function (e) {
    var row = e.target.closest('.fa-table tbody tr');
    if (!row) return;

    // 링크·버튼·입력을 직접 누른 경우는 브라우저가 알아서 처리한다.
    // 여기서 또 이동시키면 같은 이동이 두 번 일어난다.
    if (e.target.closest('a, button, input, label, select, textarea')) return;

    // 텍스트를 드래그해 읽으려던 것이면 이동하지 않는다.
    var sel = window.getSelection();
    if (sel && sel.toString().length) return;

    var href = rowHref(row);
    if (!href) return; // "데이터가 없습니다" 행에는 링크가 없다.

    // Ctrl/⌘/가운데 클릭은 링크와 같게 새 탭으로 연다.
    if (e.metaKey || e.ctrlKey || e.button === 1) window.open(href, '_blank');
    else window.location.href = href;
  });

  // 링크가 있는 행에만 포인터 커서를 준다. 빈 목록 행이 클릭될 것처럼 보이면 안 된다.
  document.querySelectorAll('.fa-table tbody tr').forEach(function (row) {
    if (rowHref(row)) row.classList.add('fa-row-link');
  });
})();

// data-auto-submit 폼: 입력이 바뀌면 조회 버튼 없이 바로 제출한다.
//
// change 를 쓰는 이유: input 이벤트로 걸면 날짜를 타이핑하는 중간 상태
// ("2026-0" 같은 값)마다 제출이 나간다. change 는 입력이 확정될 때만 뜬다.
//
// 조회 버튼은 남겨 둔다. 이 스크립트가 로드되지 않는 환경에서도 조회할 수 있어야 한다.
(function () {
  document.querySelectorAll('form[data-auto-submit]').forEach(function (form) {
    form.addEventListener('change', function (e) {
      if (!e.target.matches('input, select')) return;
      // requestSubmit() 은 form.submit() 과 달리 제약 검증(required, type=date 형식)을
      // 건너뛰지 않는다. 잘못된 날짜를 그대로 서버로 보내지 않기 위해 이쪽을 쓴다.
      if (form.requestSubmit) form.requestSubmit();
      else form.submit();
    });
  });
})();

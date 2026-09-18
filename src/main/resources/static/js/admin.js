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

// 고객사 선택 화면의 이름 검색. 카드가 많아도 서버를 다시 부르지 않는다.
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

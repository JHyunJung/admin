document.addEventListener('DOMContentLoaded', function () {
  var modalEl = document.getElementById('confirmModal');
  if (!modalEl || typeof bootstrap === 'undefined') return;
  var modal = new bootstrap.Modal(modalEl);
  var okBtn = document.getElementById('confirmModalOk');
  var defaultMessage = modalEl.querySelector('.modal-body').textContent;
  var defaultOk = okBtn.textContent;
  var targetFormId = null;
  document.querySelectorAll('[data-confirm-form]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      targetFormId = btn.getAttribute('data-confirm-form');
      modalEl.querySelector('.modal-body').textContent = btn.getAttribute('data-confirm-message') || defaultMessage;
      okBtn.textContent = btn.getAttribute('data-confirm-ok') || defaultOk;
      okBtn.classList.toggle('btn-danger', !btn.hasAttribute('data-confirm-ok'));
      okBtn.classList.toggle('btn-primary', btn.hasAttribute('data-confirm-ok'));
      modal.show();
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
    if (form.requestSubmit) form.requestSubmit();
    else form.submit();
  });
});

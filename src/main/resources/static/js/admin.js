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
    if (form) form.submit();
    modal.hide();
  });
});

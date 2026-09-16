document.addEventListener('DOMContentLoaded', function () {
  var modalEl = document.getElementById('confirmModal');
  if (!modalEl || typeof bootstrap === 'undefined') return;
  var modal = new bootstrap.Modal(modalEl);
  var targetFormId = null;
  document.querySelectorAll('[data-confirm-form]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      targetFormId = btn.getAttribute('data-confirm-form');
      var msg = btn.getAttribute('data-confirm-message');
      if (msg) modalEl.querySelector('.modal-body').textContent = msg;
      modal.show();
    });
  });
  document.getElementById('confirmModalOk').addEventListener('click', function () {
    var form = targetFormId && document.getElementById(targetFormId);
    if (form) form.submit();
    modal.hide();
  });
});

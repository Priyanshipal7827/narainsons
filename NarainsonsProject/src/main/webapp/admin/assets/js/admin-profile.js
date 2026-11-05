/** Fallback when API fields are empty. */
var NS_PROFILE_DEFAULTS = {
  full_name: "Admin",
  email: "",
  role: "admin",
  account_status: "active",
  mobile_number: "",
};

function mergeProfileAdmin(admin) {
  var base = Object.assign({}, NS_PROFILE_DEFAULTS, admin || {});
  if (typeof normalizeAdminProfile === "function") {
    base = normalizeAdminProfile(base);
  }
  return base;
}

document.addEventListener("DOMContentLoaded", function () {
  if (document.body.getAttribute("data-page") !== "profile") return;

  initAdminChromeEarly();
  fillProfilePage(mergeProfileAdmin(null));

  initAdminShell({
    onReady: function (admin) {
      initSidebarMenuFilter();
      wirePasswordToggles();
      fillProfilePage(mergeProfileAdmin(admin));
      bindProfileForms(mergeProfileAdmin(admin));
      bindProfilePhoto(mergeProfileAdmin(admin));
    },
  });
});

function bindProfilePhoto(admin) {
  var input = document.getElementById("profile-photo");
  if (!input || input.dataset.photoWired === "1") return;
  input.dataset.photoWired = "1";

  input.addEventListener("change", function () {
    var file = input.files && input.files[0];
    if (!file) return;

    if (!/^image\/(jpeg|png|webp|gif)$/i.test(file.type)) {
      showGlobalToast("Please choose a JPG, PNG, WebP, or GIF image", "error");
      input.value = "";
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      showGlobalToast("Photo must be 3 MB or smaller", "error");
      input.value = "";
      return;
    }

    var preview = document.getElementById("profile-photo-preview-img");
    var empty = document.getElementById("profile-photo-preview-empty");
    var reader = new FileReader();
    reader.onload = function () {
      if (preview) {
        preview.src = reader.result;
        preview.classList.remove("hidden");
      }
      if (empty) empty.classList.add("hidden");
      document.querySelectorAll("img[data-admin-photo]").forEach(function (img) {
        img.src = reader.result;
      });
    };
    reader.readAsDataURL(file);

    adminUploadProfilePhoto(file)
      .then(function (data) {
        nsAdminProfileCache = null;
        var updated = mergeProfileAdmin((data && data.admin) || admin);
        updated.has_profile_photo = true;
        currentAdmin = updated;
        applyAdminProfileToShell(updated);
        fillProfilePage(updated);
        showGlobalToast("Profile photo saved", "success");
      })
      .catch(function (err) {
        showGlobalToast(err.message || "Could not upload photo", "error");
      });
  });
}

var currentAdmin = null;

function fillProfilePage(admin) {
  admin = mergeProfileAdmin(admin);
  var nameInput = document.getElementById("profile-full-name");
  var emailInput = document.getElementById("profile-email-input");
  var mobileInput = document.getElementById("profile-mobile");
  if (nameInput) nameInput.value = admin.full_name || "";
  if (emailInput) emailInput.value = admin.email || "";
  if (mobileInput) mobileInput.value = admin.mobile_number || "";

  setText("kv-role", formatRole(admin.role));
  setText(
    "kv-status",
    (admin.account_status || "active").replace(/^./, function (c) {
      return c.toUpperCase();
    })
  );
  var mail = admin.email || "—";
  setText("kv-email", mail);
  var kvEmail = document.getElementById("kv-email");
  if (kvEmail) {
    if (admin.email) kvEmail.setAttribute("title", admin.email);
    else kvEmail.removeAttribute("title");
  }
  setText("kv-member-since", formatAdminDate(admin.created_at));
  setText("kv-last-updated", formatAdminDate(admin.updated_at || admin.last_login));

  var statusPill = document.getElementById("profile-status-pill");
  var statusLabel = (admin.account_status || "active").replace(/^./, function (c) {
    return c.toUpperCase();
  });
  if (statusPill) statusPill.textContent = statusLabel;

  if (typeof applyAdminProfileToShell === "function") {
    applyAdminProfileToShell(admin);
  }
}

function setText(id, text) {
  var el = document.getElementById(id);
  if (el) el.textContent = text;
}

function bindProfileForms(admin) {
  currentAdmin = mergeProfileAdmin(admin);

  var profileForm = document.getElementById("profile-form");
  if (profileForm) {
    profileForm.addEventListener("submit", async function (e) {
      e.preventDefault();
      try {
        var data = await adminApi("/api/admin/auth/profile", {
          method: "POST",
          body: JSON.stringify({
            full_name: document.getElementById("profile-full-name").value.trim(),
            mobile_number: document.getElementById("profile-mobile").value.trim(),
          }),
        });
        nsAdminProfileCache = null;
        currentAdmin = mergeProfileAdmin((data && data.admin) || currentAdmin);
        applyAdminProfileToShell(currentAdmin);
        fillProfilePage(currentAdmin);
        showGlobalToast("Profile saved successfully", "success");
      } catch (err) {
        showGlobalToast(err.message, "error");
      }
    });
  }

  var resetBtn = document.getElementById("btn-profile-reset");
  if (resetBtn) {
    resetBtn.addEventListener("click", function () {
      fillProfilePage(currentAdmin);
    });
  }

  var pwdForm = document.getElementById("password-form");
  if (pwdForm) {
    pwdForm.addEventListener("submit", async function (e) {
      e.preventDefault();
      var current = document.getElementById("pwd-current").value;
      var next = document.getElementById("pwd-new").value;
      var confirm = document.getElementById("pwd-confirm").value;
      if (next.length < 8) {
        showGlobalToast("New password must be at least 8 characters", "error");
        return;
      }
      if (next !== confirm) {
        showGlobalToast("Confirm password does not match", "error");
        return;
      }
      try {
        var changed = await adminApi("/api/admin/auth/password/change", {
          method: "POST",
          body: JSON.stringify({
            current_password: current,
            new_password: next,
          }),
        });
        // Changing the password ends old sessions; the server sends a new one for this browser.
        if (changed && changed.access_token) setAdminToken(changed.access_token);
        pwdForm.reset();
        wirePasswordToggles();
        showGlobalToast("Password changed successfully", "success");
      } catch (err) {
        showGlobalToast(err.message, "error");
      }
    });
  }
}

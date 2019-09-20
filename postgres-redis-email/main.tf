provider "bouncr" {
  account  = "admin"
  password = "password"
}

resource "bouncr_application" "rotom" {
  name         = "rotom"
  description  = "Rotom Wiki"
  pass_to      = "http://rotom:3007/rotom"
  virtual_path = "/rotom"
  top_page     = "http://localhost:3000/rotom"

  realm {
    name        = "rotom"
    description = "rotom_main"
    url         = ".*"
  }
}

resource "bouncr_user" "kawasima" {
  account = "kawasima"

  user_profiles = {
    email = "kawasima1016@example.com"
    name  = "Yoshitaka Kawasima"
  }

  password = "pass1234"
}

resource "bouncr_group" "rotom_admin" {
  name        = "rotom_admin"
  description = "Administration for Rotom"

  members = [
    "${bouncr_user.kawasima.id}",
  ]
}

resource "bouncr_role" "rotom_admin" {
  name        = "rotom_admin"
  description = "Administration role for Rotom"

  permissions = [
    "application:read",
    "realm:read",
    "user:read",
    "group:read",
    "role:read",
    "permission:read",
  ]
}

resource "bouncr_assignments" "assign" {
  assignment {
    role  = "${bouncr_role.rotom_admin.id}"
    group = "${bouncr_group.rotom_admin.id}"
    realm = "rotom"
  }

  assignment {
    role  = "${bouncr_role.rotom_admin.id}"
    group = "${bouncr_group.rotom_admin.id}"
    realm = "BOUNCR"
  }
}

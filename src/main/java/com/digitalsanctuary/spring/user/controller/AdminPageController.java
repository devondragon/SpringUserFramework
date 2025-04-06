package com.digitalsanctuary.spring.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The AdminPageController for the admin pages.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
public class AdminPageController {

  /**
   * Admin actions page.
   *
   * @return the string
   */
  @GetMapping("${admin.actionsURI:/admin/actions.html}")
  @PreAuthorize("hasAuthority('ADMIN_PRIVILEGE')")
  public String actions() {
    return "admin/actions";
  }
}

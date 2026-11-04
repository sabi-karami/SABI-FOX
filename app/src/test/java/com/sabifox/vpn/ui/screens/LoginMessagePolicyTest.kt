package com.sabifox.vpn.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginMessagePolicyTest {
    @Test fun permitsOnlyExactSecureMainFrame() {
        assertTrue(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com", -1, true, 128))
        assertTrue(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com", 443, true, 0))
        assertFalse(LoginMessagePolicy.trustedOrigin("http", "accounts.firefox.com", -1, true, 128))
        assertFalse(LoginMessagePolicy.trustedOrigin("https", "evil.accounts.firefox.com", -1, true, 128))
        assertFalse(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com.evil.test", -1, true, 128))
        assertFalse(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com", 444, true, 128))
        assertFalse(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com", -1, false, 128))
        assertFalse(LoginMessagePolicy.trustedOrigin("https", "accounts.firefox.com", -1, true, 65537))
        assertFalse(LoginMessagePolicy.trustedOrigin(null, null, -1, true, 0))
    }
}

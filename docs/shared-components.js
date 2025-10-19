/*
 * Shared Components Loader
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 * Loads shared header and footer from index.html into docs pages
 */

// Load shared components when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    loadSharedComponents();
});

// Also try loading after a short delay to ensure everything is ready
setTimeout(function() {
    // Check if footer is still empty and try loading again
    const footerContainer = document.getElementById('shared-footer');
    if (footerContainer && (!footerContainer.innerHTML || footerContainer.innerHTML.trim() === '')) {
        console.log('Retrying footer load...');
        loadFooter();
    }
}, 1000);

function loadSharedComponents() {
    // Load header
    loadHeader();
    
    // Load footer
    loadFooter();
    
    // Initialize shared functionality
    initSharedFunctionality();
}

function loadHeader() {
    // Create header container if it doesn't exist
    let headerContainer = document.getElementById('shared-header');
    if (!headerContainer) {
        headerContainer = document.createElement('div');
        headerContainer.id = 'shared-header';
        document.body.insertBefore(headerContainer, document.body.firstChild);
    }
    
    // Load header from index.html
    fetch('../index.html')
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const nav = doc.querySelector('nav.navbar');
            
            if (nav) {
                headerContainer.innerHTML = nav.outerHTML;
                
                // Fix relative paths in the loaded header
                fixRelativePaths(headerContainer);
                
                // Initialize mobile menu for the loaded header
                initMobileMenu();
                
                // Initialize language selector for the loaded header
                initSimpleLanguageSelector();
            }
        })
        .catch(error => {
            console.error('Error loading header:', error);
            // Fallback header
            headerContainer.innerHTML = createFallbackHeader();
        });
}

function loadFooter() {
    // Create footer container if it doesn't exist
    let footerContainer = document.getElementById('shared-footer');
    if (!footerContainer) {
        footerContainer = document.createElement('div');
        footerContainer.id = 'shared-footer';
        document.body.appendChild(footerContainer);
    }
    
    // Load footer from index.html
    fetch('../index.html')
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.text();
        })
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const footer = doc.querySelector('footer');
            
            if (footer) {
                footerContainer.innerHTML = footer.outerHTML;
                
                // Fix relative paths in the loaded footer
                fixRelativePaths(footerContainer);
                console.log('Footer loaded successfully from index.html');
            } else {
                console.warn('Footer not found in index.html, using fallback');
                footerContainer.innerHTML = createFallbackFooter();
            }
        })
        .catch(error => {
            console.error('Error loading footer:', error);
            // Fallback footer
            footerContainer.innerHTML = createFallbackFooter();
        });
}

function fixRelativePaths(container) {
    // Fix image paths
    const images = container.querySelectorAll('img[src^="../"]');
    images.forEach(img => {
        img.src = img.src.replace('../', '');
    });
    
    // Fix link paths - handle different directory levels
    const links = container.querySelectorAll('a[href]');
    links.forEach(link => {
        const href = link.getAttribute('href');
        
        // Fix relative paths based on current page location
        if (href.startsWith('../')) {
            // For docs/docs/ pages, we need to go up two levels
            if (window.location.pathname.includes('/docs/docs/')) {
                link.href = href.replace('../', '../../');
            } else {
                // For docs/ pages, go up one level
                link.href = href.replace('../', '');
            }
        }
        
        // Fix internal links to docs pages
        if (href.includes('user-guide.html') || 
            href.includes('developer-guide.html') || 
            href.includes('privacy-policy.html') || 
            href.includes('terms-of-service.html') || 
            href.includes('disclaimer.html') || 
            href.includes('license.html')) {
            
            // If we're in docs/docs/, add ../ prefix
            if (window.location.pathname.includes('/docs/docs/')) {
                if (!href.startsWith('../')) {
                    link.href = '../' + href;
                }
            }
        }
        
        // Fix home page link
        if (href.includes('index.html')) {
            if (window.location.pathname.includes('/docs/docs/')) {
                link.href = '../../index.html';
            } else if (window.location.pathname.includes('/docs/')) {
                link.href = '../index.html';
            }
        }
    });
    
    // Fix CSS paths
    const styles = container.querySelectorAll('link[href^="../"]');
    styles.forEach(style => {
        style.href = style.href.replace('../', '');
    });
}

function createFallbackHeader() {
    return `
        <nav class="navbar">
            <div class="nav-container">
                <a href="../index.html" class="nav-logo">
                    <img src="../images/dogecoin.svg" alt="Dogecoin Wallet" class="logo-img">
                    <span class="logo-text">Dogecoin Wallet</span>
                </a>
                <div class="nav-toggle" id="mobile-menu">
                    <span class="bar"></span>
                    <span class="bar"></span>
                    <span class="bar"></span>
                </div>
                <div class="nav-menu" id="nav-menu">
                    <a href="../index.html#features" class="nav-link">Features</a>
                    <a href="../index.html#documentation" class="nav-link">Docs</a>
                    <a href="../index.html#contribute" class="nav-link">Contribute</a>
                    <div class="language-selector">
                        <button class="language-selector-btn" id="language-selector-btn" onclick="showTranslationInstructions()">
                            <i class="fas fa-globe-americas"></i>
                            <span>Translate</span>
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>
            </div>
        </nav>
    `;
}

function createFallbackFooter() {
    // Determine the correct path prefix based on current location
    const isInDocsDocs = window.location.pathname.includes('/docs/docs/');
    const pathPrefix = isInDocsDocs ? '../' : '';
    const homePrefix = isInDocsDocs ? '../../' : '../';
    
    return `
        <footer class="footer">
            <div class="container">
                <div class="footer-content">
                    <div class="footer-section">
                        <h4>Dogecoin Wallet</h4>
                        <p>Much secure, very wow! The most advanced Dogecoin wallet for Android.</p>
                        <div class="social-links">
                            <a href="https://github.com/qlpqlp/dogecoin-wallet" target="_blank">
                                <i class="fab fa-github"></i>
                            </a>
                            <a href="https://x.com/dogecoin" target="_blank" class="x-icon">
                                <i class="fab fa-twitter"></i>
                            </a>
                        </div>
                    </div>
                    <div class="footer-section">
                        <h4>Quick Links</h4>
                        <ul>
                            <li><a href="${homePrefix}index.html">Home</a></li>
                            <li><a href="${pathPrefix}user-guide.html">User Guide</a></li>
                            <li><a href="${pathPrefix}developer-guide.html">Developer Guide</a></li>
                        </ul>
                    </div>
                    <div class="footer-section">
                        <h4>Legal</h4>
                        <ul>
                            <li><a href="${pathPrefix}privacy-policy.html">Privacy Policy</a></li>
                            <li><a href="${pathPrefix}terms-of-service.html">Terms of Service</a></li>
                            <li><a href="${pathPrefix}disclaimer.html">Disclaimer</a></li>
                            <li><a href="${pathPrefix}license.html">License</a></li>
                        </ul>
                    </div>
                </div>
                <div class="footer-bottom">
                    <p>&copy; 2024 Dogecoin Wallet. Much secure, very wow!</p>
                </div>
            </div>
        </footer>
    `;
}

function initSharedFunctionality() {
    // Initialize mobile menu functionality
    function initMobileMenu() {
        const mobileMenu = document.getElementById('mobile-menu');
        const navMenu = document.getElementById('nav-menu');
        
        if (mobileMenu && navMenu) {
            mobileMenu.addEventListener('click', function() {
                mobileMenu.classList.toggle('active');
                navMenu.classList.toggle('active');
            });
            
            // Close menu when clicking on a link
            const navLinks = document.querySelectorAll('.nav-link');
            navLinks.forEach(link => {
                link.addEventListener('click', function() {
                    mobileMenu.classList.remove('active');
                    navMenu.classList.remove('active');
                });
            });
            
            // Close menu when clicking outside
            document.addEventListener('click', function(e) {
                if (!mobileMenu.contains(e.target) && !navMenu.contains(e.target)) {
                    mobileMenu.classList.remove('active');
                    navMenu.classList.remove('active');
                }
            });
        }
    }
    
    // Initialize language selector functionality
    function initSimpleLanguageSelector() {
        const languageBtn = document.getElementById('language-selector-btn');
        if (languageBtn) {
            // Add hover effects
            languageBtn.addEventListener('mouseenter', function() {
                this.style.transform = 'translateY(-1px)';
            });
            
            languageBtn.addEventListener('mouseleave', function() {
                this.style.transform = 'translateY(0)';
            });
        }
    }
    
    // Initialize scroll effects
    function initScrollEffects() {
        let lastScrollTop = 0;
        const navbar = document.querySelector('.navbar');
        
        window.addEventListener('scroll', function() {
            const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
            
            if (scrollTop > lastScrollTop && scrollTop > 100) {
                // Scrolling down
                navbar.style.transform = 'translateY(-100%)';
            } else {
                // Scrolling up
                navbar.style.transform = 'translateY(0)';
            }
            
            lastScrollTop = scrollTop;
        });
    }
    
    // Initialize all shared functionality
    initMobileMenu();
    initSimpleLanguageSelector();
    initScrollEffects();
}

// Translation functionality (shared from main script)
function showTranslationInstructions() {
    showTranslationNotice('browser');
}

function showTranslationNotice(type = 'browser') {
    // Remove any existing notices
    const existingNotice = document.querySelector('.translation-notice');
    if (existingNotice) {
        existingNotice.remove();
    }
    
    // Create a temporary notification
    const notice = document.createElement('div');
    notice.className = 'translation-notice';
    notice.style.cssText = `
        position: fixed;
        top: 80px;
        right: 20px;
        background: var(--bg-card);
        color: var(--text-color);
        padding: 16px 20px;
        border-radius: var(--border-radius-small);
        border: 1px solid var(--primary-color);
        box-shadow: var(--shadow);
        z-index: 1002;
        font-size: 14px;
        max-width: 300px;
        backdrop-filter: blur(20px);
        animation: slideInRight 0.3s ease;
    `;
    
    const instructions = window.showBrowserTranslationInstructions ? window.showBrowserTranslationInstructions() : 'Right-click → "Translate to [Language]"';
    
    const content = `
        <div style="display: flex; align-items: center; gap: 12px;">
            <i class="fas fa-globe-americas" style="font-size: 20px; color: var(--primary-color);"></i>
            <div>
                <div style="font-weight: 600; color: var(--primary-color);">Browser Translation</div>
                <div style="font-size: 12px; color: var(--text-light); margin-top: 4px;">
                    ${instructions}
                </div>
                <div style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
                    <i class="fas fa-check-circle" style="color: var(--accent-color); margin-right: 4px;"></i>
                    Works in Chrome, Firefox, Safari, Edge!
                </div>
            </div>
        </div>
    `;
    
    notice.innerHTML = content;
    document.body.appendChild(notice);
    
    // Auto remove after 6 seconds
    setTimeout(() => {
        notice.style.animation = 'slideOutRight 0.3s ease';
        setTimeout(() => {
            if (notice.parentNode) {
                notice.parentNode.removeChild(notice);
            }
        }, 300);
    }, 6000);
}

// Add CSS animations for the notice
const translationStyle = document.createElement('style');
translationStyle.textContent = `
    @keyframes slideInRight {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOutRight {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(translationStyle);

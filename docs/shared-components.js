/*
 * Shared Components Loader
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 * Loads shared header and footer from index.html into docs pages
 */

// Load shared components when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM loaded, initializing shared components...');
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
    fetch('index.html')
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const nav = doc.querySelector('nav.navbar');
            
            if (nav) {
                console.log('Header loaded successfully');
                headerContainer.innerHTML = nav.outerHTML;
                
                // Fix relative paths in the loaded header
                fixRelativePaths(headerContainer);
                
                // Initialize mobile menu for the loaded header with delay
                setTimeout(() => {
                    console.log('Initializing mobile menu after header load...');
                    initMobileMenu();
                }, 100);
                
                // Initialize language selector for the loaded header
                initSimpleLanguageSelector();
            } else {
                console.log('Header not found in index.html');
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
    fetch('index.html')
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
            // For docs/ pages, go up one level
            link.href = href.replace('../', '');
        }
        
        // Fix internal links to docs pages
        if (href.includes('user-guide.html') || 
            href.includes('developer-guide.html') || 
            href.includes('privacy-policy.html') || 
            href.includes('terms-of-service.html') || 
            href.includes('disclaimer.html') || 
            href.includes('license.html')) {
            
            // All files are now in the same directory, so links should be relative
            if (!href.startsWith('../') && !href.startsWith('/') && !href.startsWith('http')) {
                link.href = href;
            }
        }
        
        // Fix home page link
        if (href.includes('index.html')) {
            // All files are now in the same directory, so index.html is just index.html
            if (!href.startsWith('../') && !href.startsWith('/') && !href.startsWith('http')) {
                link.href = href;
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
                <a href="index.html" class="nav-logo">
                    <img src="images/dogecoin.svg" alt="Dogecoin Wallet" class="logo-img">
                    <span class="logo-text">Dogecoin Wallet</span>
                </a>
                <div class="nav-toggle" id="mobile-menu">
                    <span class="bar"></span>
                    <span class="bar"></span>
                    <span class="bar"></span>
                </div>
                <div class="nav-menu" id="nav-menu">
                    <a href="index.html#features" class="nav-link">Features</a>
                    <a href="index.html#documentation" class="nav-link">Docs</a>
                    <a href="index.html#contribute" class="nav-link">Contribute</a>
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
                            <li><a href="index.html">Home</a></li>
                            <li><a href="user-guide.html">User Guide</a></li>
                            <li><a href="developer-guide.html">Developer Guide</a></li>
                        </ul>
                    </div>
                    <div class="footer-section">
                        <h4>Legal</h4>
                        <ul>
                            <li><a href="privacy-policy.html">Privacy Policy</a></li>
                            <li><a href="terms-of-service.html">Terms of Service</a></li>
                            <li><a href="disclaimer.html">Disclaimer</a></li>
                            <li><a href="license.html">License</a></li>
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

// Initialize mobile menu functionality
function initMobileMenu() {
    const mobileMenu = document.getElementById('mobile-menu');
    const navMenu = document.getElementById('nav-menu');
    
    if (mobileMenu && navMenu) {
        // Remove any existing event listeners by cloning the element
        const newMobileMenu = mobileMenu.cloneNode(true);
        mobileMenu.parentNode.replaceChild(newMobileMenu, mobileMenu);
        
        // Get the new reference
        const freshMobileMenu = document.getElementById('mobile-menu');
        const freshNavMenu = document.getElementById('nav-menu');
        
        if (freshMobileMenu && freshNavMenu) {
            freshMobileMenu.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                freshMobileMenu.classList.toggle('active');
                freshNavMenu.classList.toggle('active');
                console.log('Mobile menu toggled');
            });
            
            // Close menu when clicking on a link
            const navLinks = document.querySelectorAll('.nav-link');
            navLinks.forEach(link => {
                link.addEventListener('click', function() {
                    freshMobileMenu.classList.remove('active');
                    freshNavMenu.classList.remove('active');
                    console.log('Mobile menu closed via link click');
                });
            });
            
            // Close menu when clicking outside
            document.addEventListener('click', function(e) {
                if (!freshMobileMenu.contains(e.target) && !freshNavMenu.contains(e.target)) {
                    freshMobileMenu.classList.remove('active');
                    freshNavMenu.classList.remove('active');
                }
            });
            
            console.log('Mobile menu initialized successfully');
        }
    } else {
        console.log('Mobile menu elements not found, retrying...');
        // Retry after a short delay
        setTimeout(() => {
            initMobileMenu();
        }, 500);
    }
}

function initSharedFunctionality() {
    // Initialize rocket to top functionality
    initRocketToTop();
    
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

// Add DOGE smoke trail animation CSS
const dogeSmokeStyle = document.createElement('style');
dogeSmokeStyle.textContent = `
    .doge-words-container {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 999;
    }
    
    .doge-smoke-word {
        position: fixed;
        font-family: 'Comic Neue', cursive;
        font-weight: bold;
        pointer-events: none;
        z-index: 1000;
        animation: dogeSmokeTrail 2s ease-out forwards;
    }
    
    @keyframes dogeSmokeTrail {
        0% {
            opacity: 1;
            transform: scale(1) rotate(var(--rotation, 0deg)) translateY(0px) translateX(0px);
            filter: blur(0px);
        }
        20% {
            opacity: 0.9;
            transform: scale(1.1) rotate(calc(var(--rotation, 0deg) + 5deg)) translateY(-10px) translateX(var(--drift, 0px));
            filter: blur(0.5px);
        }
        40% {
            opacity: 0.7;
            transform: scale(1.2) rotate(calc(var(--rotation, 0deg) + 10deg)) translateY(-20px) translateX(calc(var(--drift, 0px) * 1.5));
            filter: blur(1px);
        }
        60% {
            opacity: 0.5;
            transform: scale(1.3) rotate(calc(var(--rotation, 0deg) + 15deg)) translateY(-30px) translateX(calc(var(--drift, 0px) * 2));
            filter: blur(1.5px);
        }
        80% {
            opacity: 0.3;
            transform: scale(1.4) rotate(calc(var(--rotation, 0deg) + 20deg)) translateY(-40px) translateX(calc(var(--drift, 0px) * 2.5));
            filter: blur(2px);
        }
        100% {
            opacity: 0;
            transform: scale(1.5) rotate(calc(var(--rotation, 0deg) + 25deg)) translateY(-50px) translateX(calc(var(--drift, 0px) * 3));
            filter: blur(3px);
        }
    }
`;
document.head.appendChild(dogeSmokeStyle);

// Add rocket to top functionality
function initRocketToTop() {
    // Create rocket button if it doesn't exist
    let rocketButton = document.getElementById('rocketToTop');
    if (!rocketButton) {
        rocketButton = document.createElement('button');
        rocketButton.id = 'rocketToTop';
        rocketButton.className = 'rocket-to-top';
        rocketButton.title = 'Back to Top';
        rocketButton.innerHTML = `
            <div class="rocket">
                <div class="rocket-body">
                    <div class="body"></div>
                    <div class="fin fin-left"></div>
                    <div class="fin fin-right"></div>
                    <div class="window"></div>
                </div>
                <div class="exhaust-flame"></div>
                <ul class="exhaust-fumes">
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                </ul>
                <ul class="star">
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                    <li></li>
                </ul>
            </div>
        `;
        document.body.appendChild(rocketButton);
    }
    
    // Create DOGE words container if it doesn't exist
    let dogeWordsContainer = document.getElementById('dogeWordsContainer');
    if (!dogeWordsContainer) {
        dogeWordsContainer = document.createElement('div');
        dogeWordsContainer.id = 'dogeWordsContainer';
        dogeWordsContainer.className = 'doge-words-container';
        document.body.appendChild(dogeWordsContainer);
    }
    
    // Show/hide rocket button based on scroll position
    function toggleRocketButton() {
        if (window.pageYOffset > 300) {
            rocketButton.classList.add('show');
        } else {
            rocketButton.classList.remove('show');
        }
    }
    
    // Smooth scroll to top when rocket is clicked
    rocketButton.addEventListener('click', function() {
        // Add launching class for animation
        rocketButton.classList.add('launching');
        
        // Create DOGE smoke animation
        createDogeSmokeAnimation();
        
        // Smooth scroll to top
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
        
        // Remove launching class after animation
        setTimeout(() => {
            rocketButton.classList.remove('launching');
        }, 2000);
    });
    
    // Listen for scroll events
    window.addEventListener('scroll', toggleRocketButton);
    
    // Initial check
    toggleRocketButton();
}

// Create DOGE smoke animation
function createDogeSmokeAnimation() {
    const container = document.getElementById('dogeWordsContainer');
    if (!container) return;
    
    // Clear previous animations
    container.innerHTML = '';
    
    // Create multiple DOGE words
    for (let i = 0; i < 15; i++) {
        setTimeout(() => {
            createDogeSmoke(container);
        }, i * 100);
    }
}

// Create individual DOGE smoke word
function createDogeSmoke(container) {
    const dogeWord = document.createElement('div');
    dogeWord.className = 'doge-smoke-word';
    dogeWord.textContent = 'DOGE';
    
    // Random positioning around rocket
    const rocketRect = document.getElementById('rocketToTop').getBoundingClientRect();
    const centerX = rocketRect.left + rocketRect.width / 2;
    const centerY = rocketRect.top + rocketRect.height / 2;
    
    const angle = Math.random() * Math.PI * 2;
    const distance = 50 + Math.random() * 100;
    const x = centerX + Math.cos(angle) * distance;
    const y = centerY + Math.sin(angle) * distance;
    
    dogeWord.style.left = x + 'px';
    dogeWord.style.top = y + 'px';
    dogeWord.style.fontSize = (20 + Math.random() * 15) + 'px';
    dogeWord.style.color = `hsl(${Math.random() * 360}, 70%, 60%)`;
    dogeWord.style.position = 'fixed';
    dogeWord.style.pointerEvents = 'none';
    dogeWord.style.zIndex = '1000';
    dogeWord.style.fontWeight = 'bold';
    dogeWord.style.textShadow = '2px 2px 4px rgba(0,0,0,0.5)';
    dogeWord.style.animation = 'dogeSmokeTrail 2s ease-out forwards';
    
    // Random drift and rotation
    const drift = (Math.random() - 0.5) * 40;
    const rotation = (Math.random() - 0.5) * 30;
    dogeWord.style.setProperty('--drift', drift + 'px');
    dogeWord.style.setProperty('--rotation', rotation + 'deg');
    
    container.appendChild(dogeWord);
    
    // Remove after animation
    setTimeout(() => {
        if (dogeWord.parentNode) {
            dogeWord.parentNode.removeChild(dogeWord);
        }
    }, 2000);
}

// Modern Dogecoin Wallet Website JavaScript
function initWebsite() {
    console.log('Initializing modern Dogecoin Wallet website...');
    
    // Apply dark mode by default
    document.documentElement.classList.add('dark-mode');
    console.log('Applied dark mode theme');
    
    // Initialize other features
    initMobileMenu();
    initScrollEffects();
    initAnimations();
    initSmoothScrolling();
    initStoreButtons();
    initHeroVideo();
    initRadioDogeTooltip();
    initApkModal();
}

// Mobile menu functionality
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

// Scroll effects and animations
function initScrollEffects() {
    // Navbar scroll effect
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
    
    // Intersection Observer for fade-in animations
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };
    
    const observer = new IntersectionObserver(function(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
            }
        });
    }, observerOptions);
    
    // Observe elements for animation
    const animatedElements = document.querySelectorAll('.feature-card, .screenshot-item, .doc-card, .contribute-way');
    animatedElements.forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(30px)';
        el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
        observer.observe(el);
    });
}

// Modern animations
function initAnimations() {
    // Parallax effect for hero section
    window.addEventListener('scroll', function() {
        const scrolled = window.pageYOffset;
        const hero = document.querySelector('.hero');
        if (hero) {
            const rate = scrolled * -0.5;
            hero.style.transform = `translateY(${rate}px)`;
        }
    });
    
    // Floating animation for hero phone
    const heroPhone = document.querySelector('.hero-phone');
    if (heroPhone) {
        setInterval(() => {
            heroPhone.style.transform = `translateY(${Math.sin(Date.now() * 0.001) * 10}px)`;
        }, 16);
    }
    
    // Gradient animation for buttons
    const buttons = document.querySelectorAll('.btn');
    buttons.forEach(button => {
        button.addEventListener('mouseenter', function() {
            this.style.background = 'linear-gradient(45deg, #ffc107, #ff8f00, #ffc107)';
            this.style.backgroundSize = '200% 200%';
            this.style.animation = 'gradientShift 0.5s ease';
        });
        
        button.addEventListener('mouseleave', function() {
            this.style.background = '';
            this.style.backgroundSize = '';
            this.style.animation = '';
        });
    });
}

// Smooth scrolling for navigation links
function initSmoothScrolling() {
    const navLinks = document.querySelectorAll('a[href^="#"]');
    
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            const targetId = this.getAttribute('href');
            const targetSection = document.querySelector(targetId);
            
            if (targetSection) {
                const offsetTop = targetSection.offsetTop - 70; // Account for fixed navbar
                
                window.scrollTo({
                    top: offsetTop,
                    behavior: 'smooth'
                });
            }
        });
    });
}

// Add CSS for gradient animation
const style = document.createElement('style');
style.textContent = `
    @keyframes gradientShift {
        0% { background-position: 0% 50%; }
        50% { background-position: 100% 50%; }
        100% { background-position: 0% 50%; }
    }
    
    .navbar {
        transition: transform 0.3s ease;
    }
`;
document.head.appendChild(style);

// Initialize website when DOM is loaded
document.addEventListener('DOMContentLoaded', initWebsite);

// Add loading animation
window.addEventListener('load', function() {
    document.body.classList.add('loaded');
    
    // Add loaded class styles
    const loadedStyle = document.createElement('style');
    loadedStyle.textContent = `
        body:not(.loaded) * {
            animation-play-state: paused !important;
        }
        
        body.loaded .hero-title {
            animation: slideInUp 0.8s ease forwards;
        }
        
        body.loaded .hero-description {
            animation: slideInUp 0.8s ease 0.2s forwards;
        }
        
        body.loaded .hero-buttons {
            animation: slideInUp 0.8s ease 0.4s forwards;
        }
        
        @keyframes slideInUp {
            from {
                opacity: 0;
                transform: translateY(30px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
    `;
    document.head.appendChild(loadedStyle);
});

// Store button functionality
function initStoreButtons() {
    const storeButtons = document.querySelectorAll('.store-btn');
    
    storeButtons.forEach(button => {
        // Prevent default link behavior
        button.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Show "Much Soon!" message
            showMuchSoonMessage(this);
        });
        
        // Add click animation
        button.addEventListener('mousedown', function() {
            this.style.transform = 'translateY(0) scale(0.95)';
        });
        
        button.addEventListener('mouseup', function() {
            this.style.transform = 'translateY(-2px) scale(1.05)';
        });
        
        button.addEventListener('mouseleave', function() {
            this.style.transform = 'translateY(-2px) scale(1)';
        });
    });
}

// Show "Much Soon!" message
function showMuchSoonMessage(button) {
    // Create message element
    const message = document.createElement('div');
    message.innerHTML = 'Much Soon! <i class="fas fa-paw" style="color: #000; margin-left: 8px;"></i>';
    message.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: linear-gradient(135deg, #ffc107, #ff8f00);
        color: #000;
        padding: 20px 30px;
        border-radius: 15px;
        font-size: 1.5rem;
        font-weight: 700;
        font-family: 'Comic Neue', cursive;
        z-index: 10000;
        box-shadow: 0 10px 30px rgba(255, 193, 7, 0.5);
        animation: muchSoonBounce 0.6s ease;
        pointer-events: none;
    `;
    
    // Add bounce animation
    const style = document.createElement('style');
    style.textContent = `
        @keyframes muchSoonBounce {
            0% {
                transform: translate(-50%, -50%) scale(0.5);
                opacity: 0;
            }
            50% {
                transform: translate(-50%, -50%) scale(1.1);
                opacity: 1;
            }
            100% {
                transform: translate(-50%, -50%) scale(1);
                opacity: 1;
            }
        }
    `;
    document.head.appendChild(style);
    
    // Add to page
    document.body.appendChild(message);
    
    // Remove after 2 seconds
    setTimeout(() => {
        message.style.animation = 'muchSoonBounce 0.3s ease reverse';
        setTimeout(() => {
            if (message.parentNode) {
                message.parentNode.removeChild(message);
            }
            if (style.parentNode) {
                style.parentNode.removeChild(style);
            }
        }, 300);
    }, 2000);
}

// Hero video initialization
function initHeroVideo() {
    const video = document.querySelector('.phone-video');
    const phoneScreen = document.querySelector('.phone-screen');
    
    if (!video || !phoneScreen) {
        console.log('Hero video elements not found');
        return;
    }
    
    console.log('Initializing hero video...');
    
    // Show loading state
    phoneScreen.classList.add('loading');
    
    // Handle video events
    video.addEventListener('loadstart', () => {
        console.log('Video loading started');
        phoneScreen.classList.add('loading');
    });
    
    video.addEventListener('canplay', () => {
        console.log('Video can start playing');
        phoneScreen.classList.remove('loading');
    });
    
    video.addEventListener('playing', () => {
        console.log('Video is playing');
        phoneScreen.classList.remove('loading');
    });
    
    video.addEventListener('error', (e) => {
        console.warn('Video failed to load:', e);
        phoneScreen.classList.remove('loading');
        // Fallback to static image will be shown
    });
    
    // Ensure video plays on mobile devices
    video.addEventListener('loadeddata', () => {
        video.play().catch(e => {
            console.log('Autoplay prevented:', e);
            // Video will show poster image as fallback
        });
    });
    
    // Pause video when not in viewport to save battery
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                video.play().catch(e => console.log('Video play prevented:', e));
            } else {
                video.pause();
            }
        });
    }, { threshold: 0.5 });
    
    observer.observe(video);
}

// RadioDoge tooltip functionality
function initRadioDogeTooltip() {
    const backPhoneContainer = document.querySelector('.back-phone-container');
    const tooltip = document.querySelector('.radiodoge-tooltip');
    
    if (!backPhoneContainer || !tooltip) {
        console.log('RadioDoge tooltip elements not found');
        return;
    }
    
    console.log('Initializing RadioDoge tooltip...');
    
    let hideTimeout = null;
    let isTooltipVisible = false;
    
    // Function to show tooltip
    function showTooltip() {
        if (hideTimeout) {
            clearTimeout(hideTimeout);
            hideTimeout = null;
        }
        tooltip.style.opacity = '1';
        tooltip.style.visibility = 'visible';
        isTooltipVisible = true;
    }
    
    // Function to hide tooltip with delay
    function hideTooltip() {
        hideTimeout = setTimeout(() => {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
        }, 3000); // 3 second delay
    }
    
    // Handle hover events for back phone
    backPhoneContainer.addEventListener('mouseenter', () => {
        showTooltip();
    });
    
    backPhoneContainer.addEventListener('mouseleave', () => {
        hideTooltip();
    });
    
    // Handle click events
    backPhoneContainer.addEventListener('click', (e) => {
        e.preventDefault();
        
        if (isTooltipVisible) {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
            if (hideTimeout) {
                clearTimeout(hideTimeout);
                hideTimeout = null;
            }
        } else {
            showTooltip();
        }
    });
    
    // Close tooltip when clicking outside
    document.addEventListener('click', (e) => {
        if (!backPhoneContainer.contains(e.target) && !tooltip.contains(e.target)) {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
            if (hideTimeout) {
                clearTimeout(hideTimeout);
                hideTimeout = null;
            }
        }
    });
    
    // Handle tooltip button click
    const tooltipBtn = tooltip.querySelector('.tooltip-btn');
    if (tooltipBtn) {
        tooltipBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent closing the tooltip
            // The link will navigate to the RadioDoge documentation
        });
    }
}

// APK Download Modal functionality
function initApkModal() {
    const modal = document.getElementById('apkModal');
    const downloadBtn = document.getElementById('downloadApkBtn');
    const closeBtn = document.querySelector('.close');
    const cancelBtn = document.getElementById('cancelDownload');
    
    if (!modal || !downloadBtn) return;
    
    // Open modal when APK button is clicked
    downloadBtn.addEventListener('click', function(e) {
        e.preventDefault();
        modal.style.display = 'block';
        document.body.style.overflow = 'hidden'; // Prevent background scrolling
    });
    
    // Close modal when X is clicked
    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        });
    }
    
    // Close modal when Cancel button is clicked
    if (cancelBtn) {
        cancelBtn.addEventListener('click', function() {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        });
    }
    
    // Close modal when clicking outside of it
    window.addEventListener('click', function(e) {
        if (e.target === modal) {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        }
    });
    
    // Close modal with Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && modal.style.display === 'block') {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        }
    });
}
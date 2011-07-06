/*
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo licencia GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.cliente;

import java.awt.Component;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Vector;

import javax.security.auth.callback.PasswordCallback;
import javax.swing.JOptionPane;

import es.gob.afirma.callbacks.CachePasswordCallback;
import es.gob.afirma.exceptions.AOCancelledOperationException;
import es.gob.afirma.exceptions.AOCertificateException;
import es.gob.afirma.exceptions.AOCertificateKeyException;
import es.gob.afirma.exceptions.AOCertificatesNotFoundException;
import es.gob.afirma.exceptions.AOKeyStoreManagerException;
import es.gob.afirma.keystores.AOKeyStoreManager;
import es.gob.afirma.keystores.AOKeyStoreManagerFactory;
import es.gob.afirma.keystores.AOKeystoreAlternativeException;
import es.gob.afirma.misc.AOConstants.AOKeyStore;
import es.gob.afirma.misc.AOCryptoUtil;
import es.gob.afirma.misc.Platform;
import es.gob.afirma.ui.AOUIManager;

/** Almacena una configuracui&oacute;n para el almac&eacute;bn establecido del
 * Cliente @firma. Gestiona su inicializaci&oacute;n y la selecci&oacute;n de
 * certificados por parte del usuario. */
final class KeyStoreConfigurationManager {

    /** Almac&eacute;n de claves configurado. */
    private AOKeyStore ks = null;

    /** Almac&eacute;n por defecto para el sistema en cuesti&oacute;n. */
    private AOKeyStore defaultKeyStore = null;

    /** Alias de certificado seleccionado. */
    private String selectedAlias = null;

    /** Manejador para los almacenes de claves. */
    private AOKeyStoreManager ksManager = null;

    /** Referencia al certificado con clave privada seleccionado. */
    private PrivateKeyEntry ke = null;

    /** Ruta del almac&eacute;n de claves seleccionado (para alamcenes P12/PFX,
     * JKS, P11...). */
    private String ksPath = null;

    /** Contrase&ntilde;a del almacen de claves seleccionado. */
    private String ksPassword = null;

    /** Mensaje de error establecido. */
    private String errorMessage = null;

    /** Componente padre sobre el que mostrar los di&aacute;logos modales. */
    private Component parent = null;

    /** Indica si debe advertirse al usuario de que inserte los dispositivos
     * criptogr&aacute;ficos externos antes de inicializar el almac&eacute;n de
     * certificados. */
    private boolean showLoadingWarning = false;

    private boolean showExpiratedCertificates = false;
    private boolean mandatoryCert = false;
    private final Vector<CertificateFilter> certFilters = new Vector<CertificateFilter>();

    /** Construye la configuraci&oacute;n por defecto para el Cliente, pudiendo
     * variar el almac&eacute;n seg&uacute;n el sistema operativo:
     * <ul>
     * <li><b>Windows:</b> Almac&eacute;n de Windows/Internet Explorer.</li>
     * <li><b>Sistemas UNIX:</b> Almac&eacute;n Mozilla.</li>
     * <li><b>Mac OS X:</b> Almac&eacute;n de Apple Mac OS X.</li>
     * <li><b>Otros:</b> Almac&eacute;n PKCS#12/PFX.</li>
     * </ul>
     * @param keyStore
     *        Almac&eacute;n de claves por defecto.
     * @param parent
     *        Componente padre sobre el que se mostrar&aacute;n los
     *        di&aacute;logos modales necesarios. */
    KeyStoreConfigurationManager(final AOKeyStore keyStore, final Component parent) {
        this(keyStore);
        this.parent = parent;
    }

    /** Construye la configuraci&oacute;n por defecto para el Cliente, pudiendo
     * variar el almac&eacute;n seg&uacute;n el sistema operativo:
     * <ul>
     * <li><b>Windows:</b> Almac&eacute;n de Windows/Internet Explorer.</li>
     * <li><b>Sistemas Unix:</b> Almac&eacute;n Mozilla.</li>
     * <li><b>Mac OS X:</b> Almac&eacute;n de Apple Mac OS X.</li>
     * <li><b>Otros:</b> Almac&eacute;n PKCS#12/PFX.</li>
     * </ul>
     * @param keyStore
     *        Almac&eacute;n de claves por defecto. */
    private KeyStoreConfigurationManager(final AOKeyStore keyStore) {
        this.ks = this.defaultKeyStore = (keyStore != null ? keyStore : getDefaultKeyStore());
    }

    /** Recupera el almac&eacute;n de claves por defecto para el sistema
     * operativo actual.
     * @return Almac&eacute;n de claves por defecto. */
    private AOKeyStore getDefaultKeyStore() {
        if (Platform.getOS().equals(Platform.OS.WINDOWS))
         {
            return AOKeyStore.WINDOWS; // Sistemas Windows
        }
        if (Platform.getOS().equals(Platform.OS.LINUX) || Platform.getOS().equals(Platform.OS.SOLARIS)) {
            return AOKeyStore.MOZ_UNI;
        }
        if (Platform.getOS().equals(Platform.OS.MACOSX)) {
            return AOKeyStore.APPLE;
        }

        // Otros sistemas
        return AOKeyStore.PKCS12;
    }

    void resetFilters() {
        certFilters.removeAllElements();
    }

    void addCertFilter(final CertificateFilter certFilter) {
        if (certFilter != null) {
            certFilters.add(certFilter);
        }
    }

    /** Reestablece el almac&eacute;n de certificados por defecto y reinicia su
     * configuraci&oacute;n. */
    void initialize() {
        this.ks = defaultKeyStore;
        this.selectedAlias = null;
        this.ksManager = null;
        this.errorMessage = null;
        this.ke = null;
        this.showLoadingWarning = false;
        this.resetFilters();
    }

    /** Inicializa el repositorio de certificados establecido.
     * @param path
     *        Ruta al repositorio
     * @param password
     *        Contrase&ntilde;a del almac&eacute;n de claves
     * @throws AOKeystoreAlternativeException
     *         Cuando ocurre un error durante la inicializaci&oacute;n */
    private void initKeyStore(final String path, final String password) throws AOCancelledOperationException, AOKeystoreAlternativeException {

        if (this.showLoadingWarning) {
            JOptionPane.showMessageDialog(this.parent, AppletMessages.getString("SignApplet.13"), //$NON-NLS-1$
                                          AppletMessages.getString("SignApplet.658"), //$NON-NLS-1$
                                          JOptionPane.WARNING_MESSAGE);
        }

        // Para evitar la perdida de las excepciones que se emitan se relanzaran
        // estas cuando hereden de
        // RuntimeException y se devolvera como valor de retorno en caso
        // contrario. Si la devolucion del
        // metodo es null se entendera que la operacion finalizo correctamente
        this.ksManager = AOKeyStoreManagerFactory.getAOKeyStoreManager(this.ks, this.ksPath, null, this.getKeystorePasswordCallback(), this.parent);
    }

    /** Cambia el tipo de almac&eacute;n de claves establecido. Si existe una
     * configuraci&oacute;n previa (contrase&ntilde;a del almacen, alias
     * seleccionado,...) es recomendable utilizar previamente {@link #initialize()}.
     * @param keyStore
     *        Nuevo almac&eacute;n de claves. */
    void changeKeyStore(final AOKeyStore keyStore) {

        if (keyStore == null) {
            throw new NullPointerException("Es obligatorio asignar un almacen de claves"); //$NON-NLS-1$
        }

        this.ks = keyStore;

        // Inicializamos la configuracion interna, la externa se respeta porque
        // puede haberse
        // establecido, teniendo en cuenta el cambio
        this.ke = null;
        this.ksManager = null;
    }

    /** Indica si hay un certificado seleccionado.
     * @return Devuelve {@code true} si hay un certificado seleccionado. */
    boolean isSelectedCertificate() {
        return ke != null;
    }

    /** Selecciona un certificado con clave privada del almac&eacute;n, ya sea a
     * trav&eacute;s de la configuraci&oacute;n proporcionada o
     * solicit&aacute;ndoselo al usuario.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancela la operaci&oacute;n.
     * @throws AOCertificateException
     *         Cuando no se pueda seleccionar un certificado.
     * @throws AOCertificateKeyException
     *         Cuando no se puede extraer la clave privada de un
     *         certificado.
     * @throws AOKeyStoreManagerException
     *         Cuando no se pueda inicializar el almac&eacute;n de
     *         certificados.
     * @throws AOCertificatesNotFoundException
     *         Cuando no se encuentran certificados v&aacute;lido en el
     *         almac&eacute;n.
     * @throws AOKeystoreAlternativeException
     *         Cuando no se pueda inicializar el almac&eacute;n de
     *         certificados pero existe un almac&eacute;n alternativo */
    void selectCertificate() throws AOCancelledOperationException,
                            AOCertificateException,
                            AOCertificateKeyException,
                            AOKeyStoreManagerException,
                            AOCertificatesNotFoundException,
                            AOKeystoreAlternativeException {
        this.selectCertificate(true);
    }

    /** Selecciona un certificado del almac&eacute;n, ya sea a trav&eacute;s de
     * la configuraci&oacute;n proporcionada o solicit&aacute;ndoselo al
     * usuario.
     * @param checkPrivateKey
     *        Si es {@code true}, filtra los certificados que no tienen
     *        clave privada.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancela la operaci&oacute;n.
     * @throws AOCertificateException
     *         Cuando no se pueda seleccionar un certificado.
     * @throws AOCertificateKeyException
     *         Cuando no se puede extraer la clave privada de un
     *         certificado.
     * @throws AOKeyStoreManagerException
     *         Cuando no se pueda inicializar el almac&eacute;n de
     *         certificados.
     * @throws AOCertificatesNotFoundException
     *         Cuando no se encuentran certificados v&aacute;lido en el
     *         almac&eacute;n. */
    private void selectCertificate(final boolean checkPrivateKey) throws AOCancelledOperationException,
                                                                 AOCertificateException,
                                                                 AOCertificateKeyException,
                                                                 AOKeyStoreManagerException,
                                                                 AOCertificatesNotFoundException,
                                                                 AOKeystoreAlternativeException {

        if (ksManager == null) {
            try {
                this.initKeyStore(this.ksPath, this.ksPassword);
            }
            catch (final AOKeystoreAlternativeException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new AOKeyStoreManagerException("No se ha podido inicializar el almacen de certificados", e); //$NON-NLS-1$
            }
        }

        if (selectedAlias == null) {
            try {
                selectedAlias = this.showCertSelectionDialog(ksManager.getAliases(), checkPrivateKey);
            }
            catch (final AOCancelledOperationException e) {
                throw e;
            }
            catch (final AOCertificatesNotFoundException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new AOCertificateException("Error al seleccionar un certificado del repositorio", e); //$NON-NLS-1$
            }
        }

        // En caso de ser todos certificados con clave privada, obtenemos la
        // referencia a esta
        if (checkPrivateKey) {
            this.ke = this.ksManager.getKeyEntry(this.selectedAlias, this.getCertificatePasswordCallback());
        }
    }

    /** Recupera el certificado con el alias indicado del repositorio actual. Si
     * no existe ning&uacute;n certificado con ese alias, se devolver&aacute; {@code null}.
     * @param alias
     *        Alias del certificado que deseamos recuperar.
     * @return Certificado con el alias seleccionado.
     * @throws AOCancelledOperationException
     *         Cuando se cancela la operaci&oacute;n.
     * @throws AOKeyStoreManagerException
     *         Cuando no se ha podido inicializar el almac&eacute;n de
     *         certificados. */
    Certificate getCertificate(final String alias) throws AOKeyStoreManagerException, AOKeystoreAlternativeException {
        if (ksManager == null) {
            try {
                this.initKeyStore(this.ksPath, this.ksPassword);
            }
            catch (final AOCancelledOperationException e) {
                throw e;
            }
            catch (final AOKeystoreAlternativeException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new AOKeyStoreManagerException("No se ha podido inicializar el almacen de certificados", e); //$NON-NLS-1$
            }
        }

        return ksManager.getCertificate(alias);
    }

    /** Recupera los alias del almacen seleccionado. Si ocurre alg&uacute;n error
     * durante la operaci&oacute;n se devuelve un array vac&iacute;o.
     * @return Certificado con el alias seleccionado.
     * @throws AOCancelledOperationException
     *         Cuando se cancela la operaci&oacute;n.
     * @throws AOKeyStoreManagerException
     *         Cuando no se ha podido inicializar el almac&eacute;n de
     *         certificados. */
    String[] getArrayCertificateAlias() throws AOKeyStoreManagerException, AOKeystoreAlternativeException {
        if (ksManager == null) {
            try {
                this.initKeyStore(this.ksPath, this.ksPassword);
            }
            catch (final AOCancelledOperationException e) {
                throw e;
            }
            catch (final AOKeystoreAlternativeException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new AOKeyStoreManagerException("No se ha podido inicializar el almacen de certificados", e); //$NON-NLS-1$
            }
        }

        return ksManager.getAliases();
    }

    /** Recupera el gestor del almac&eacute;n de certificados actual ya
     * inicializado.
     * @return Gestor del almac&eacute;n de certificados.
     * @throws AOCancelledOperationException
     *         Cuando se cancela la operaci&oacute;n.
     * @throws AOKeyStoreManagerException
     *         Cuando no se ha podido inicializar el almac&eacute;n. */
    AOKeyStoreManager getKeyStoreManager() throws AOKeyStoreManagerException, AOKeystoreAlternativeException {
        if (ksManager == null) {
            try {
                this.initKeyStore(this.ksPath, this.ksPassword);
            }
            catch (final AOCancelledOperationException e) {
                throw e;
            }
            catch (final AOKeystoreAlternativeException e) {
                throw e;
            }
            catch (final Exception e) {
                throw new AOKeyStoreManagerException("No se ha podido inicializar el almacen de certificados", e); //$NON-NLS-1$
            }
        }

        return ksManager;
    }

    /** Devuelve el almac&eacute;n de certificados configurado.
     * @return Almac&eacute;n de certificados. */
    AOKeyStore getKeyStore() {
        return ks;
    }

    /** Recupera la referencia a la clave privada del certificado.
     * @return Clave privada del certificado. */
    PrivateKeyEntry getCertificateKeyEntry() {
        return this.ke;
    }

    /** Muestra el di&aacute;logo de selecci&oacute;n de certificados del
     * almac&eacute;n seleccionado, aplicando los filtros de certificado de ser
     * necesario.
     * @param certAlias
     *        Alias de los certificados sobre los que hay que aplicar el
     *        filtro.
     * @param checkPrivateKey
     *        Indica si se deben mostrar &uacute;nicamente los certificados
     *        con clave privada.
     * @return Alias real (con el que fue dado de alta en el almac&eacute;n de
     *         claves) del certificado seleccionado.
     * @throws AOCertificatesNotFoundException
     *         Cuando no se encuentran certificados que cumplan el filtro.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancel&oacute; la operaci&oacute;n. */
    private String showCertSelectionDialog(final String[] certAlias, final boolean checkPrivateKey) throws AOCertificatesNotFoundException,
                                                                                                   AOCancelledOperationException {
        return AOUIManager.showCertSelectionDialog(certAlias, // Aliases
                                                   (this.ksManager == null) ? null : this.ksManager.getKeyStores(), // KeyStores
                                                   this.parent, // Panel sobre el que mostrar el dialogo
                                                   checkPrivateKey, // Comprobar accesibilidad de claves privadas
                                                   true, // Advierte si el certificado esta caducado
                                                   this.showExpiratedCertificates, // Muestra certificados caducados
                                                   this.certFilters, // Filtros para los certificados
                                                   this.isMandatoryCert() // Solo se admite un certificado
        );
    }

    /** Recupera el PasswordCallback apropiado para los certificados del
     * almac&eacute;n concreto o, si se especific&oacute; una contrase&ntilde;a,
     * uno con ella prefijada.
     * @return PasswordCallback para la obtenci&oacute;n de la clave del
     *         certificado. */
    private PasswordCallback getCertificatePasswordCallback() {
        return this.ksPassword == null
                                      ? AOCryptoUtil.getCertificatePC(this.ks, this.parent)
                                      : new CachePasswordCallback(this.ksPassword.toCharArray());
    }

    /** Recupera el PasswordCallback apropiado para el almacen de claves
     * configurado o, si se especific&oacute; una contrase&ntilde;a, uno con
     * ella prefijada.
     * @return PasswordCallback para el acceso al almac&eacute;n. */
    private PasswordCallback getKeystorePasswordCallback() {
        return this.ksPassword == null
                                      ? AOCryptoUtil.getPreferredPCB(this.ks, this.parent)
                                      : new CachePasswordCallback(this.ksPassword.toCharArray());
    }

    /** Recupera el certificado seleccionado.
     * @return Certificado seleccionado o nulo si no hab&iacute;a ninguno. */
    X509Certificate getSelectedCertificate() {
        if (this.ke != null) {
            final Certificate cert = ke.getCertificate();
            if (cert instanceof X509Certificate) {
                return (X509Certificate) cert;
            }
        }
        if (this.selectedAlias != null) {
            return this.ksManager.getCertificate(this.selectedAlias);
        }
        throw new UnsupportedOperationException("No se puede recuperar el Certificado X509");
    }

    /** Recupera el alias del certificado seleccionado.
     * @return Alias del certificado. */
    String getSelectedAlias() {
        return selectedAlias;
    }

    /** Establece el alias del certificado que debe utilizarse.
     * @param selectedAlias
     *        Alias del certificado. */
    void setSelectedAlias(final String selectedAlias) {
        if (this.selectedAlias != null && !this.selectedAlias.equals(selectedAlias)) {
            this.ke = null;
        }
        this.selectedAlias = selectedAlias;
    }

    /** Establece la contrase&ntilde; para el almac&eacute;n de claves.
     * @param password
     *        Contrase&ntilde;a para el almac&eacute;n */
    void setKsPassword(final String password) {
        this.ksPassword = password;
    }

    /** Establece la ruta del almac&eacute;n de claves.
     * @param path
     *        Ruta absoluta del almac&eacute;n. */
    void setKsPath(final String path) {
        this.ksPath = (path == null || path.trim().equals("")) ? null : path; //$NON-NLS-1$
    }

    /** Indica si se seleccionar&aacute; autom&aacute;ticamente el certificado de
     * firma.
     * @return Devuelve {@code true} si autoselecciona el certificado. */
    boolean isMandatoryCert() {
        return this.mandatoryCert;
    }

    /** Establece si deben mostrarse los certificados caducados.
     * @param showExpiratedCertificates
     *        {@code true} para mostrar los certificados caducados. */
    void setShowExpiratedCertificates(final boolean showExpiratedCerts) {
        this.showExpiratedCertificates = showExpiratedCerts;
    }

    /** Establece que se seleccione autom&aacute;ticamente el certificado cuando
     * s&oacute;lo quede uno despu&eacute;s de pasar los distintos filtros. Si
     * quedase m&aacute;s de un certificado se lanzar&iacute;a una
     * excepci&oacute;n.
     * @param mandatoryCert
     *        {@code true} para indicar que se seleccione
     *        autom&aacute;ticamente el certificado. */
    void setMandatoryCert(final boolean mCert) {
        this.mandatoryCert = mCert;
    }

    /** Establece que se muestre o no, antes de cargar un almac&eacute;n de
     * certificados, una advertencia del tipo
     * "Inserte su tarjeta inteligente o cualquier otro dispositivo antes de continuar"
     * .
     * @param showWarning
     *        Si es {@code true} se mostrar&aacute; la advertencia. */
    void setLoadingWarning(final boolean showWarning) {
        this.showLoadingWarning = showWarning;
    }

    /** Recupera el mensaje de error identificado en la configurac&oacute;n
     * actual para el almac&eacute;n. Si no se ha identificado ning&uacute;n
     * error se devolver&aacute; {@code null}.
     * @return Mensaje de error. */
    String getErrorMessage() {
        return errorMessage;
    }
}

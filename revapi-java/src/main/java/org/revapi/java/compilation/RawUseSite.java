package org.revapi.java.compilation;

import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class RawUseSite {
    private final UseSite.Type useType;
    private final String siteClass;
    private final String siteName;
    private final String siteDescriptor;
    private final SiteType siteType;
    private final int sitePosition;

    public RawUseSite(UseSite.Type useType, SiteType siteType, String siteClass, String siteName, String siteDescriptor) {
        this(useType, siteType, siteClass, siteName, siteDescriptor, -1);
    }

    public RawUseSite(UseSite.Type useType, SiteType siteType, String siteClass, String siteName, String siteDescriptor,
        int sitePosition) {

        this.useType = useType;
        this.siteType = siteType;
        this.siteClass = siteClass;
        this.siteName = siteName;
        this.siteDescriptor = siteDescriptor;
        this.sitePosition = sitePosition;
    }

    public UseSite.Type getUseType() {
        return useType;
    }

    public SiteType getSiteType() {
        return siteType;
    }

    public String getSiteClass() {
        return siteClass;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getSiteDescriptor() {
        return siteDescriptor;
    }

    public int getSitePosition() {
        return sitePosition;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RawUseSite[");
        sb.append("siteClass='").append(siteClass).append('\'');
        sb.append(", siteDescriptor='").append(siteDescriptor).append('\'');
        sb.append(", siteName='").append(siteName).append('\'');
        sb.append(", sitePosition=").append(sitePosition);
        sb.append(", siteType=").append(siteType);
        sb.append(", useType=").append(useType);
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RawUseSite that = (RawUseSite) o;

        if (sitePosition != that.sitePosition) {
            return false;
        }
        if (!siteClass.equals(that.siteClass)) {
            return false;
        }
        if (siteDescriptor != null ? !siteDescriptor.equals(that.siteDescriptor) : that.siteDescriptor != null) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (siteName != null ? !siteName.equals(that.siteName) : that.siteName != null) {
            return false;
        }

        return siteType == that.siteType && useType == that.useType;
    }

    @Override
    public int hashCode() {
        int result = useType.hashCode();
        result = 31 * result + siteClass.hashCode();
        result = 31 * result + (siteName != null ? siteName.hashCode() : 0);
        result = 31 * result + (siteDescriptor != null ? siteDescriptor.hashCode() : 0);
        result = 31 * result + siteType.hashCode();
        result = 31 * result + sitePosition;
        return result;
    }

    public enum SiteType {
        CLASS, FIELD, METHOD, METHOD_PARAMETER
    }
}

/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *                                                                            *
 *  Contributors:                                                             *
 *    Carlos Ruiz - GlobalQSS:                                                *
 *      FR 3132033 - Make payment export class configurable per bank          *
 *****************************************************************************/
package org.compiere.apps.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;

import org.compiere.model.MLookupFactory;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPaymentBatch;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Language;
import org.compiere.util.ValueNamePair;

public class PayPrint {

	/** Window No */
	public int m_WindowNo = 0;
	/** Used Bank Account */
	public int m_C_BankAccount_ID = -1;
	/** Export Class for Bank Account */
	public String m_PaymentExportClass = null;

	/** Payment Information */
	public MPaySelectionCheck[] m_checks = null;
	/** Payment Batch */
	public MPaymentBatch m_batch = null;
	/** Logger */
	public static CLogger log = CLogger.getCLogger(PayPrint.class);

	public ArrayList<KeyNamePair> getPaySelectionData() {
		ArrayList<KeyNamePair> data = new ArrayList<KeyNamePair>();

		log.config("");
		int AD_Client_ID = Env.getAD_Client_ID(Env.getCtx());

		// Yamel Senih 2013-05-15 10:37:49
		// Change SQL, hide generated Payments
		String sql = "SELECT sp.C_PaySelection_ID, sp.Name || ' - ' || sp.TotalAmt "
				+ "FROM C_PaySelection sp "
				+ "INNER JOIN C_PaySelectionCheck csp ON(csp.C_PaySelection_ID = sp.C_PaySelection_ID) "
				+ "WHERE sp.AD_Client_ID= ? "
				+ "AND sp.Processed='Y' "
				+ "AND sp.IsActive='Y' "
				+ "AND csp.C_Payment_ID IS NULL "
				+ "GROUP BY sp.C_PaySelection_ID, sp.Name, sp.TotalAmt "
				+ "ORDER BY sp.PayDate DESC";

		// Load PaySelect
		/*
		 * String sql =
		 * "SELECT C_PaySelection_ID, Name || ' - ' || TotalAmt FROM C_PaySelection "
		 * + "WHERE AD_Client_ID=? AND Processed='Y' AND IsActive='Y'" +
		 * "ORDER BY PayDate DESC";
		 */

		// End Yamel Senih

		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, AD_Client_ID);
			ResultSet rs = pstmt.executeQuery();
			//
			while (rs.next()) {
				KeyNamePair pp = new KeyNamePair(rs.getInt(1), rs.getString(2));
				data.add(pp);
			}
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}

		return data;
	}

	public String bank;
	public String currency;
	public BigDecimal balance;

	/**
	 * PaySelect changed - load Bank
	 */
	public void loadPaySelectInfo(int C_PaySelection_ID) {
		// load Banks from PaySelectLine
		m_C_BankAccount_ID = -1;
		String sql = "SELECT ps.C_BankAccount_ID, b.Name || ' ' || ba.AccountNo," // 1..2
				+ " c.ISO_Code, CurrentBalance, ba.PaymentExportClass " // 3..5
				+ "FROM C_PaySelection ps"
				+ " INNER JOIN C_BankAccount ba ON (ps.C_BankAccount_ID=ba.C_BankAccount_ID)"
				+ " INNER JOIN C_Bank b ON (ba.C_Bank_ID=b.C_Bank_ID)"
				+ " INNER JOIN C_Currency c ON (ba.C_Currency_ID=c.C_Currency_ID) "
				+ "WHERE ps.C_PaySelection_ID=? AND ps.Processed='Y' AND ba.IsActive='Y'";
		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				m_C_BankAccount_ID = rs.getInt(1);
				bank = rs.getString(2);
				currency = rs.getString(3);
				balance = rs.getBigDecimal(4);
				m_PaymentExportClass = rs.getString(5);
			} else {
				m_C_BankAccount_ID = -1;
				bank = "";
				currency = "";
				balance = Env.ZERO;
				m_PaymentExportClass = null;
				log.log(Level.SEVERE,
						"No active BankAccount for C_PaySelection_ID="
								+ C_PaySelection_ID);
			}
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}
	} // loadPaySelectInfo

	/**
	 * Bank changed - load PaymentRule
	 */
	public ArrayList<ValueNamePair> loadPaymentRule(int C_PaySelection_ID) {
		ArrayList<ValueNamePair> data = new ArrayList<ValueNamePair>();

		// load PaymentRule for Bank
		int AD_Reference_ID = 195; // MLookupInfo.getAD_Reference_ID("All_Payment Rule");
		Language language = Language.getLanguage(Env.getAD_Language(Env
				.getCtx()));
		MLookupInfo info = MLookupFactory.getLookup_List(language,
				AD_Reference_ID);
		String sql = info.Query.substring(0, info.Query.indexOf(" ORDER BY"))
				+ " AND "
				+ info.KeyColumn
				+ " IN (SELECT PaymentRule FROM C_PaySelectionCheck WHERE C_PaySelection_ID=?) "
				+ info.Query.substring(info.Query.indexOf(" ORDER BY"));
		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			ResultSet rs = pstmt.executeQuery();
			//
			while (rs.next()) {
				ValueNamePair pp = new ValueNamePair(rs.getString(2),
						rs.getString(3));
				data.add(pp);
			}
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}

		if (data.size() == 0)
			log.config("PaySel=" + C_PaySelection_ID + ", BAcct="
					+ m_C_BankAccount_ID + " - " + sql);

		return data;
	} // loadPaymentRule

	public String noPayments;
	public Integer documentNo;

	/**
	 * PaymentRule changed - load DocumentNo, NoPayments, enable/disable EFT,
	 * Print
	 */
	public String loadPaymentRuleInfo(int C_PaySelection_ID, String PaymentRule) {
		String msg = null;

		String sql = "SELECT COUNT(*) " + "FROM C_PaySelectionCheck "
				+ "WHERE C_PaySelection_ID=?";
		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_PaySelection_ID);
			ResultSet rs = pstmt.executeQuery();
			//
			if (rs.next())
				noPayments = String.valueOf(rs.getInt(1));
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}

		// DocumentNo
		sql = "SELECT CurrentNext " + "FROM C_BankAccountDoc "
				+ "WHERE C_BankAccount_ID=? AND PaymentRule=? AND IsActive='Y'";
		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, m_C_BankAccount_ID);
			pstmt.setString(2, PaymentRule);
			ResultSet rs = pstmt.executeQuery();
			//
			if (rs.next())
				documentNo = new Integer(rs.getInt(1));
			else {
				log.log(Level.SEVERE,
						"VPayPrint.loadPaymentRuleInfo - No active BankAccountDoc for C_BankAccount_ID="
								+ m_C_BankAccount_ID
								+ " AND PaymentRule="
								+ PaymentRule);
				msg = "VPayPrintNoDoc";
			}
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}

		return msg;
	} // loadPaymentRuleInfo
}
